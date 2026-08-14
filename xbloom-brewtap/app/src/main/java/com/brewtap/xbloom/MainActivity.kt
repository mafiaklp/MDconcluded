package com.brewtap.xbloom

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private enum class Mode { READ, WRITE, RESTORE }
    private val io = Executors.newSingleThreadExecutor()
    private var nfc: NfcAdapter? = null
    private var mode = Mode.READ
    private var backup: CardDump? = null
    private var decoded: XBloomCardCodec.Decoded? = null

    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var readButton: Button
    private lateinit var writeButton: Button
    private lateinit var restoreButton: Button
    private lateinit var doseField: EditText
    private lateinit var grindField: EditText
    private lateinit var rpmField: EditText
    private lateinit var poursBox: LinearLayout
    private val pourFields = mutableListOf<EditText>()

    private val ivory = Color.rgb(246,242,233)
    private val ink = Color.rgb(23,21,18)
    private val muted = Color.rgb(116,111,103)
    private val espresso = Color.rgb(57,43,36)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        setContentView(buildUi())
    }

    private fun rounded(color:Int, radius:Float)=GradientDrawable().apply{setColor(color);cornerRadius=radius}
    private fun label(text:String)=TextView(this).apply{this.text=text;textSize=13f;setTextColor(muted)}
    private fun field():EditText=EditText(this).apply{setTextColor(ink);textSize=16f;setSingleLine(true);background=rounded(Color.WHITE,18f);setPadding(18,12,18,12)}

    private fun buildUi(): ScrollView {
        val scroll=ScrollView(this).apply{setBackgroundColor(ivory)}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,36,36,60)}
        scroll.addView(root, ViewGroup.LayoutParams(-1,-2))
        root.addView(TextView(this).apply{text="BrewTap Recipe Lab";textSize=31f;setTextColor(ink);setTypeface(typeface,Typeface.BOLD)})
        root.addView(TextView(this).apply{text="Read → decode → edit → write → verify. Original card bytes are backed up before editing.";textSize=15f;setTextColor(muted);setPadding(0,6,0,24)})

        readButton=Button(this).apply{text="1  READ & DECODE CARD";setTextColor(Color.WHITE);background=rounded(espresso,36f);setOnClickListener{arm(Mode.READ)}}
        root.addView(readButton,LinearLayout.LayoutParams(-1,62))

        summary=TextView(this).apply{text="No card loaded yet.";textSize=15f;setTextColor(ink);setPadding(4,20,4,18)}
        root.addView(summary)

        root.addView(label("DOSE (g)")); doseField=field();root.addView(doseField)
        root.addView(label("GRIND SIZE").apply{setPadding(0,12,0,0)}); grindField=field();root.addView(grindField)
        root.addView(label("GRINDER RPM").apply{setPadding(0,12,0,0)}); rpmField=field();root.addView(rpmField)
        root.addView(label("POURS: volume,temp,flow(×10),pattern(0 center/1 circular/2 spiral),pause,agitation").apply{setPadding(0,18,0,6)})
        poursBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(poursBox)

        writeButton=Button(this).apply{text="2  WRITE EDITED RECIPE";isEnabled=false;setTextColor(Color.WHITE);background=rounded(espresso,36f);setOnClickListener{arm(Mode.WRITE)}}
        root.addView(writeButton,LinearLayout.LayoutParams(-1,62).apply{topMargin=20})
        restoreButton=Button(this).apply{text="RESTORE ORIGINAL CARD";isEnabled=false;setOnClickListener{arm(Mode.RESTORE)}}
        root.addView(restoreButton,LinearLayout.LayoutParams(-1,58).apply{topMargin=10})

        status=TextView(this).apply{text=if(nfc==null)"NFC unavailable on this phone." else "Ready.";textSize=14f;setTextColor(muted);setPadding(4,20,4,0)}
        root.addView(status)
        return scroll
    }

    private fun arm(next:Mode){
        val adapter=nfc ?: run{status.text="NFC unavailable.";return}
        if(!adapter.isEnabled){status.text="Turn NFC on and try again.";return}
        if(next!=Mode.READ && backup==null){status.text="Read the card first.";return}
        mode=next
        status.text=when(next){Mode.READ->"Hold the xBloom card to the phone…";Mode.WRITE->"Hold THE SAME card to write and verify…";Mode.RESTORE->"Hold THE SAME card to restore the original bytes…"}
        adapter.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null)
    }

    override fun onTagDiscovered(tag: Tag){
        io.execute{
            val message=try{
                when(mode){
                    Mode.READ -> {
                        val dump=NfcVRecipeLab().read(tag)
                        val d=XBloomCardCodec.decode(dump.bytes)
                        backup=dump;decoded=d
                        saveBackup(dump)
                        runOnUiThread{populate(d);writeButton.isEnabled=true;restoreButton.isEnabled=true}
                        "✓ CARD DECODED — original backup saved"
                    }
                    Mode.WRITE -> {
                        val b=backup ?: error("No backup")
                        val uid=tag.id.joinToString(":"){"%02X".format(it.toInt() and 0xFF)}
                        require(uid.equals(b.uidHex,true)){"This is a different card"}
                        val recipe=buildEditedRecipe()
                        val result=NfcVRecipeLab().writeRecipe(tag,recipe)
                        "✓ WRITTEN & VERIFIED (${result.verifiedBytes} bytes)\nTake the card to xBloom and test it."
                    }
                    Mode.RESTORE -> {
                        val b=backup ?: error("No backup")
                        NfcVRecipeLab().restore(tag,b)
                        "✓ ORIGINAL CARD RESTORED & VERIFIED"
                    }
                }
            }catch(e:Exception){"ERROR: ${e.message ?: e.javaClass.simpleName}"}
            runOnUiThread{try{nfc?.disableReaderMode(this)}catch(_:Exception){};status.text=message}
        }
    }

    private fun populate(d:XBloomCardCodec.Decoded){
        val r=d.recipe
        doseField.setText(r.doseGrams.toString());grindField.setText(r.grindSize.toString());rpmField.setText(r.grinderRpm.toString())
        poursBox.removeAllViews();pourFields.clear()
        r.pours.forEachIndexed{idx,p->
            val e=field().apply{setText("${p.volumeMl},${p.temperatureC},${p.flowRateTenthsMlPerSec},${p.pattern.code},${p.pauseSeconds},${p.agitation}")}
            poursBox.addView(label("Pour ${idx+1}"));poursBox.addView(e,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=8});pourFields+=e
        }
        summary.text="XID ${r.name}  •  ${r.doseGrams}g  •  ${r.totalWaterMl}ml  •  1:${d.storedRatio}\nGrind ${r.grindSize} @ ${r.grinderRpm} RPM  •  CRC 0x%02X".format(d.crc)
    }

    private fun buildEditedRecipe():BrewRecipe{
        val original=decoded ?: error("Read a card first")
        val dose=doseField.text.toString().toInt();val grind=grindField.text.toString().toInt();val rpm=rpmField.text.toString().toInt()
        val pours=pourFields.mapIndexed{idx,e->
            val v=e.text.toString().split(',').map{it.trim().toInt()};require(v.size==6){"Pour ${idx+1} needs 6 comma-separated values"}
            val pattern=when(v[3]){0->PourPattern.CENTERED;1->PourPattern.CIRCULAR;2->PourPattern.SPIRAL;else->error("Invalid pattern in pour ${idx+1}")}
            BrewPour(v[0],v[1],v[2],pattern,v[5],v[4])
        }
        val total=pours.sumOf{it.volumeMl}
        require(total % dose == 0){"Total water $total ml must divide evenly by dose $dose g (integer xBloom ratio)"}
        return BrewRecipe(original.recipe.name,dose,total,grind,rpm,pours.first().temperatureC,pours,original.recipe.cupType)
    }

    private fun saveBackup(d:CardDump){
        val prefs=getSharedPreferences("card_backups",MODE_PRIVATE)
        prefs.edit().putString(d.uidHex,Base64.encodeToString(d.bytes,Base64.NO_WRAP)).apply()
    }

    override fun onPause(){super.onPause();try{nfc?.disableReaderMode(this)}catch(_:Exception){}}
    override fun onDestroy(){io.shutdownNow();super.onDestroy()}
}
