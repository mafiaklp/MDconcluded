package com.brewtap.xbloom

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

class XBloomBleClient(private val context: Context) {
    interface Callback {
        fun onState(message: String)
        fun onLoaded(deviceName: String)
        fun onError(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var scanner = adapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var callback: Callback? = null
    private var recipe: BrewRecipe? = null
    private var deviceName = "xBloom"

    private val writeUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    fun sendRecipe(recipe: BrewRecipe, callback: Callback) {
        this.recipe = recipe
        this.callback = callback
        if (adapter == null || !adapter.isEnabled) { callback.onError("Bluetooth is off or unavailable."); return }
        callback.onState("Searching for xBloom…")
        scanner = adapter.bluetoothLeScanner
        scanner?.startScan(scanCallback) ?: callback.onError("Bluetooth scanner unavailable.")
        handler.postDelayed({
            scanner?.stopScan(scanCallback)
            if (gatt == null) callback.onError("xBloom not found. Disconnect the official xBloom app and try again.")
        }, 10000)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        gatt?.disconnect(); gatt?.close(); gatt = null
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: return
            if (!name.startsWith("XBLOOM", ignoreCase = true)) return
            deviceName = name
            scanner?.stopScan(this)
            callback?.onState("Connecting to $name…")
            gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { callback?.onError("Connection failed ($status)."); close(); return }
            if (newState == BluetoothProfile.STATE_CONNECTED) { callback?.onState("Connected. Preparing recipe…"); g.discoverServices() }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) gatt = null
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { callback?.onError("Could not discover xBloom services."); return }
            val ch = g.services.asSequence().flatMap { it.characteristics.asSequence() }.firstOrNull { it.uuid == writeUuid }
            val r = recipe
            if (ch == null || r == null) { callback?.onError("xBloom command channel not found."); return }
            callback?.onState("Sending adaptive recipe…")
            val packets = listOf(
                XBloomBleProtocol.buildHandshake(),
                XBloomBleProtocol.buildBackHome(),
                XBloomBleProtocol.buildBypass(r),
                XBloomBleProtocol.buildSetCup(),
                XBloomBleProtocol.buildDirectRecipePacket(r)
            )
            writePackets(g, ch, packets, 0)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writePackets(g: BluetoothGatt, ch: BluetoothGattCharacteristic, packets: List<ByteArray>, index: Int) {
        if (index >= packets.size) {
            callback?.onLoaded(deviceName)
            handler.postDelayed({ close() }, 1200)
            return
        }
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ch.value = packets[index]
        val ok = g.writeCharacteristic(ch)
        if (!ok) { callback?.onError("Recipe transfer failed at step ${index + 1}."); close(); return }
        handler.postDelayed({ writePackets(g, ch, packets, index + 1) }, 450)
    }
}
