package com.brewtap.xbloom

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

class XBloomDirectBleClient(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onDone: (Result<Unit>) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var gatt: BluetoothGatt? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var command: BluetoothGattCharacteristic? = null
    private var status: BluetoothGattCharacteristic? = null
    private var recipe: BrewRecipe? = null
    private var finished = false

    fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true

    @SuppressLint("MissingPermission")
    fun load(recipe: BrewRecipe) {
        if (!hasPermissions()) return fail("Bluetooth permission required")
        val ad = adapter ?: return fail("Bluetooth unavailable")
        if (!ad.isEnabled) return fail("Turn Bluetooth on")
        this.recipe = recipe
        finished = false
        onStatus("Searching for xBloom…")
        scanner = ad.bluetoothLeScanner
        scanner?.startScan(scanCallback)
        handler.postDelayed({ if (!finished && gatt == null) { stopScan(); fail("xBloom not found. Close the xBloom app and keep the machine awake.") } }, 9000)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() { runCatching { scanner?.stopScan(scanCallback) }; scanner = null }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = runCatching { result.device.name ?: result.scanRecord?.deviceName.orEmpty() }.getOrDefault("")
            val hasService = result.scanRecord?.serviceUuids?.any { it.uuid.toString().equals(XBloomBleProtocol.SERVICE_UUID, true) } == true
            if (!name.uppercase().startsWith("XBLOOM") && !hasService) return
            stopScan()
            onStatus("Found ${name.ifBlank { "xBloom" }} • connecting…")
            gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onStatus("Connected • discovering xBloom service…")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !finished) fail("xBloom disconnected")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            val service = g.getService(UUID.fromString(XBloomBleProtocol.SERVICE_UUID)) ?: return fail("xBloom BLE service not found")
            command = service.getCharacteristic(UUID.fromString(XBloomBleProtocol.COMMAND_UUID)) ?: return fail("Command channel not found")
            status = service.getCharacteristic(UUID.fromString(XBloomBleProtocol.STATUS_UUID)) ?: return fail("Status channel not found")
            val s = status!!
            g.setCharacteristicNotification(s, true)
            val cccd = s.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else { @Suppress("DEPRECATION") cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(cccd) }
            } else beginLoad()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) { beginLoad() }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) { handleNotify(value) }
        @Deprecated("Deprecated in Java") override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) { handleNotify(characteristic.value ?: byteArrayOf()) }
    }

    @SuppressLint("MissingPermission")
    private fun write(frame: ByteArray) {
        val g = gatt ?: return
        val c = command ?: return
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        else { @Suppress("DEPRECATION") c.value = frame; @Suppress("DEPRECATION") g.writeCharacteristic(c) }
    }

    private fun beginLoad() {
        val r = recipe ?: return fail("No recipe")
        val frames = XBloomBleProtocol.loadFrames(r)
        onStatus("Opening xBloom session…")
        write(frames[0])
        handler.postDelayed({ write(XBloomBleProtocol.statusQuery()); onStatus("xBloom ready • staging recipe…") }, 500)
        handler.postDelayed({ write(frames[1]) }, 2500)
        handler.postDelayed({ write(frames[2]) }, 2900)
        handler.postDelayed({ write(frames[3]); onStatus("Recipe sent • waiting for machine…") }, 3300)
        handler.postDelayed({ if (!finished) fail("Recipe sent but xBloom did not report ARMED. Check machine firmware/state.") }, 14000)
    }

    private fun handleNotify(data: ByteArray) {
        if (XBloomBleProtocol.isArmedNotification(data)) {
            finished = true
            onStatus("✓ Recipe loaded — approve on xBloom")
            cleanup()
            onDone(Result.success(Unit))
        }
    }

    private fun fail(message: String) {
        if (finished) return
        finished = true
        onStatus(message)
        cleanup()
        onDone(Result.failure(IllegalStateException(message)))
    }

    @SuppressLint("MissingPermission")
    fun cleanup() { stopScan(); runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }; gatt = null }
}
