package com.brewtap.xbloom

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.brewtap.xbloom.domain.*
import com.brewtap.xbloom.photo.AnalyzedCoffeeDraft
import com.brewtap.xbloom.photo.CoffeeBagAnalyzer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class BrewTapActivity : Activity() {
    private val bg = Color.rgb(247,244,237)
    private val paper = Color.rgb(255,253,248)
    private val ink = Color.rgb(25,22,19)
    private val muted = Color.rgb(108,101,94)
    private val espresso = Color.rgb(55,42,35)
    private val copper = Color.rgb(166,104,68)
    private val soft = Color.rgb(239,227,214)
    private val border = Color.rgb(227,219,209)

    private var profile: CoffeeProfile? = null
    private var hot: GeneratedRecipe? = null
    private var iced: GeneratedRecipe? = null
    private var mode = BrewMode.HOT
    private var selected: GeneratedRecipe? = null
    private var directClient: XBloomDirectBleClient? = null
    private var pendingSend: BrewRecipe? = null

    companion object {
        private const val PICK_IMAGE = 501
        private const val REQ_BLE = 502
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showHome() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun round(color:Int,r:Int=22,stroke:Boolean=false)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat();if(stroke)setStroke(dp(1),border)}
    private fun txt(s:String,size:Float=15f,color:Int=ink,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(color);includeFontPadding=false;if(bold)setTypeface(typeface,Typeface.BOLD)}
    private fun btn(s:String,primary:Boolean=true,onClick:()->Unit)=Button(this).apply{text=s;isAllCaps=false;textSize=14f;setTextColor(if(primary)Color.WHITE else espresso);background=round(if(primary)espresso else soft,18);minHeight=dp(56);setOnClickListener{onClick()}}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(20));background=round(paper,22)}
    private fun field(hint:String,value:String="",numeric:Boolean=false)=EditText(this).apply{this.hint=hint;setText(value);textSize=16f;setTextColor(ink);setHintTextColor(Color.rgb(145,138,128));background=round(Color.WHITE,16,true);setPadding(dp(16),dp(14),dp(16),dp(14));minHeight=dp(56);if(numeric)inputType=android.text.InputType.TYPE_CLASS_NUMBER}

    private fun page(title:String,subtitle:String):Pair<ScrollView,LinearLayout>{
        val scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(24),dp(20),dp(42))}
        scroll.addView(body,ViewGroup.LayoutParams(-1,-2))
        body.addView(txt("BREWTAP",12f,copper,true));body.addView(txt(title,34f,ink,true).apply{setPadding(0,dp(8),0,dp(7))});body.addView(txt(subtitle,16f,muted).apply{setPadding(0,0,0,dp(22))})
        return scroll to body
    }

    private fun nav(body:LinearLayout,current:String){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(0,dp(24),0,0)}
        listOf("Home" to {showHome()},"Coffee" to {if(profile==null)showAddCoffee() else showRecipes()},"Brew" to {showBrew()},"Settings" to {showSettings()}).forEach{(name,go)->
            row.addView(Button(this).apply{text=name;isAllCaps=false;textSize=11f;setTextColor(if(name==current)Color.WHITE else espresso);background=round(if(name==current)espresso else Color.TRANSPARENT,16);setOnClickListener{go()}},LinearLayout.LayoutParams(0,dp(50),1f).apply{marginStart=dp(2);marginEnd=dp(2)})
        };body.addView(row)
    }

    private fun showHome(){
        val(s,b)=page("Brew smarter.","Build a competition-minded Hot and Iced recipe, then load it directly to xBloom.")
        b.addView(card().apply{
            addView(txt("SMART RECIPE ENGINE",11f,copper,true));addView(txt("One coffee. Two intentional brews.",25f,ink,true).apply{setPadding(0,dp(9),0,dp(7))})
            addView(txt("Default dose is 15 g because many xBloom recipes are built around it — but dose stays editable for every coffee.",15f,muted))
            addView(btn("Add a coffee",true){showAddCoffee()},LinearLayout.LayoutParams(-1,dp(58)).apply{topMargin=dp(18)})
        })
        profile?.let{p->b.addView(card().apply{addView(txt("CURRENT COFFEE",11f,copper,true));addView(txt(p.name,23f,ink,true).apply{setPadding(0,dp(8),0,dp(5))});addView(btn("Open Hot / Iced",true){showRecipes()},LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(16)})},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(16)})}
        nav(b,"Home");setContentView(s)
    }

    private fun showAddCoffee(draft:AnalyzedCoffeeDraft?=null){
        val(s,b)=page("New coffee","Scan the bag or enter the coffee yourself. Review everything before generation.")
        b.addView(card().apply{addView(txt("PHOTO AI",11f,copper,true));addView(txt("Read the bag",23f,ink,true).apply{setPadding(0,dp(8),0,dp(6))});addView(txt("OCR fills what it can; every field remains editable.",14f,muted));addView(btn("Scan coffee bag",true){pickImage()},LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(15)})})
        val c=card();c.addView(txt("COFFEE PROFILE",11f,copper,true));c.addView(txt("Recipe inputs",23f,ink,true).apply{setPadding(0,dp(8),0,dp(14))})
        val name=field("Coffee name",draft?.name.orEmpty());val country=field("Country / origin",draft?.country.orEmpty());val process=field("Process — washed, natural, anaerobic…",draft?.process.orEmpty());val altitude=field("Altitude (m)",draft?.altitudeM?.toString().orEmpty(),true);val notes=field("Tasting notes — comma separated",draft?.tastingNotes?.joinToString(", ").orEmpty());val dose=field("Dose (g)","15",true)
        listOf(name,country,process,altitude,notes,dose).forEach{c.addView(it,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(11)})}
        c.addView(txt("ROAST LEVEL",11f,muted,true).apply{setPadding(dp(2),dp(2),0,dp(6))})
        val roast=Spinner(this).apply{adapter=ArrayAdapter(this@BrewTapActivity,android.R.layout.simple_spinner_dropdown_item,RoastLevel.values().map{it.name.replace('_',' ')});setSelection(RoastLevel.values().indexOf(draft?.roastLevel?:RoastLevel.UNKNOWN));background=round(Color.WHITE,16,true);minimumHeight=dp(56)}
        c.addView(roast,LinearLayout.LayoutParams(-1,dp(56)))
        c.addView(btn("Generate Hot + Iced",true){
            val doseG=dose.text.toString().toIntOrNull()?:15
            if(doseG !in 10..25){Toast.makeText(this,"Dose must be 10–25 g",Toast.LENGTH_LONG).show();return@btn}
            val p=CoffeeProfile(name=name.text.toString().trim(),country=country.text.toString().trim(),process=process.text.toString().trim(),roastLevel=RoastLevel.values()[roast.selectedItemPosition],altitudeM=altitude.text.toString().toIntOrNull(),tastingNotes=notes.text.toString().split(',').map{it.trim()}.filter{it.isNotBlank()},source=if(draft==null)CoffeeSource.MANUAL else CoffeeSource.PHOTO)
            if(!p.isGeneratable()){Toast.makeText(this,"Add coffee name plus useful coffee information first.",Toast.LENGTH_LONG).show();return@btn}
            profile=p;hot=SmartRecipeEngine.generate(p,RecipeIntent(BrewMode.HOT,doseGrams=doseG));iced=SmartRecipeEngine.generate(p,RecipeIntent(BrewMode.ICED,doseGrams=doseG));mode=BrewMode.HOT;selected=hot;showRecipes()
        },LinearLayout.LayoutParams(-1,dp(60)).apply{topMargin=dp(18)})
        b.addView(c,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(16)});nav(b,"Coffee");setContentView(s)
    }

    private fun pickImage(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"},PICK_IMAGE)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=PICK_IMAGE||resultCode!=RESULT_OK||data?.data==null)return;Toast.makeText(this,"Reading coffee bag…",Toast.LENGTH_SHORT).show();runCatching{InputImage.fromFilePath(this,data.data!!)}.onSuccess{img->TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img).addOnSuccessListener{showAddCoffee(CoffeeBagAnalyzer.analyze(it.text))}.addOnFailureListener{showAddCoffee()}}.onFailure{showAddCoffee()}}

    private fun showRecipes(){
        val p=profile?:return showAddCoffee();val g=(if(mode==BrewMode.HOT)hot else iced)?:return;selected=g
        val(s,b)=page(p.name,listOf(p.country,p.process,p.roastLevel.name.replace('_',' ')).filter{it.isNotBlank()&&it!="UNKNOWN"}.joinToString(" • "))
        val sw=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};sw.addView(btn("Hot",mode==BrewMode.HOT){mode=BrewMode.HOT;showRecipes()},LinearLayout.LayoutParams(0,dp(54),1f));sw.addView(btn("Iced",mode==BrewMode.ICED){mode=BrewMode.ICED;showRecipes()},LinearLayout.LayoutParams(0,dp(54),1f).apply{marginStart=dp(8)});b.addView(sw)
        b.addView(card().apply{
            addView(txt(if(mode==BrewMode.HOT)"HOT — RECOMMENDED" else "ICED — RECOMMENDED",11f,copper,true));addView(txt(g.expectedCup.summary,19f,ink,true).apply{setPadding(0,dp(9),0,dp(14))});addView(txt("${g.recipe.doseGrams} g  •  ${g.recipe.totalWaterMl} g brew water  •  Grind ${g.recipe.grindSize}",16f,ink,true));addView(txt("${g.recipe.temperatureC}°C  •  Grinder ${g.recipe.grinderRpm} RPM",14f,muted).apply{setPadding(0,dp(5),0,0)});g.icedSplit?.let{addView(txt("${it.brewWaterMl} g hot brew + ${it.iceGrams} g ice = ${it.targetBeverageWaterMl} g final water",14f,copper,true).apply{setPadding(0,dp(13),0,0)})};addView(btn("Tune taste",false){tune(g)},LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(18)});addView(btn("Use this recipe",true){selected=g;showBrew()},LinearLayout.LayoutParams(-1,dp(58)).apply{topMargin=dp(9)})
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)})
        b.addView(card().apply{addView(txt("POUR PLAN",11f,copper,true));g.recipe.pours.forEachIndexed{i,pour->addView(txt("Pour ${i+1} — ${pour.volumeMl} g · ${pour.temperatureC}°C · ${pour.flowRateTenthsMlPerSec/10.0} ml/s · ${pour.pattern.name.lowercase()} · pause ${pour.pauseSeconds}s",14f,if(i==0)ink else muted).apply{setPadding(0,dp(11),0,0)})}},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)})
        nav(b,"Coffee");setContentView(s)
    }

    private fun tune(g:GeneratedRecipe){val goals=TasteGoal.values().filter{it!=TasteGoal.RECOMMENDED};AlertDialog.Builder(this).setTitle("Tune the cup").setItems(goals.map{it.name.lowercase().replace('_',' ')}.toTypedArray()){_,i->val t=RecipeTuner.tune(g,goals[i]);if(g.mode==BrewMode.HOT)hot=t else iced=t;selected=t;showRecipes()}.setNegativeButton("Cancel",null).show()}

    private fun showBrew(){
        val g=selected;val(s,b)=page("Brew","Load the selected recipe directly to xBloom Studio over Bluetooth.")
        if(g==null){b.addView(card().apply{addView(txt("No recipe selected",22f,ink,true));addView(btn("Create a coffee",true){showAddCoffee()},LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(16)})})}
        else{
            val status=txt("Ready. Keep xBloom awake and disconnect the official xBloom app first.",14f,muted)
            b.addView(card().apply{
                addView(txt("DIRECT TO xBLOOM",11f,copper,true));addView(txt(g.recipe.name,23f,ink,true).apply{setPadding(0,dp(8),0,dp(6))});addView(txt("${g.recipe.doseGrams} g  •  ${g.recipe.totalWaterMl} g brew water  •  Grind ${g.recipe.grindSize} @ ${g.recipe.grinderRpm} RPM",15f,muted));g.icedSplit?.let{addView(txt("Add ${it.iceGrams} g ice before brewing.",15f,copper,true).apply{setPadding(0,dp(8),0,0)})};addView(status.apply{setPadding(0,dp(18),0,0)});addView(btn("Send recipe to xBloom",true){sendToMachine(g.recipe,status)},LinearLayout.LayoutParams(-1,dp(60)).apply{topMargin=dp(18)});addView(txt("Loading only arms the machine. BrewTap does not auto-start hot water; approve the brew on xBloom.",12f,muted).apply{setPadding(0,dp(12),0,0)})
            })
        };nav(b,"Brew");setContentView(s)
    }

    private fun sendToMachine(recipe:BrewRecipe,status:TextView){
        pendingSend=recipe
        if(Build.VERSION.SDK_INT>=31 && (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)){
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT),REQ_BLE);status.text="Allow Nearby devices, then BrewTap will send the recipe.";return
        }
        directClient?.cleanup();directClient=XBloomDirectBleClient(this,{runOnUiThread{status.text=it}},{result->runOnUiThread{if(result.isFailure)Toast.makeText(this,result.exceptionOrNull()?.message?:"xBloom load failed",Toast.LENGTH_LONG).show()}});directClient?.load(recipe)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_BLE && grantResults.isNotEmpty() && grantResults.all{it==PackageManager.PERMISSION_GRANTED}){Toast.makeText(this,"Nearby devices allowed — tap Send recipe again.",Toast.LENGTH_SHORT).show()}else if(requestCode==REQ_BLE)Toast.makeText(this,"Bluetooth permission is required to load xBloom recipes.",Toast.LENGTH_LONG).show()}

    private fun showSettings(){val(s,b)=page("Settings","BrewTap 1.4.0");b.addView(card().apply{addView(txt("UPDATE READY",11f,copper,true));addView(txt("Stable signing from v1.4 onward",22f,ink,true).apply{setPadding(0,dp(8),0,dp(6))});addView(txt("Future BrewTap APKs signed with this same development key can install over the previous BrewTap version without deleting app data.",14f,muted));addView(txt("Direct BLE loading is based on the reverse-engineered xBloom Studio protocol. Loading never starts a brew automatically.",14f,muted).apply{setPadding(0,dp(12),0,0)})});nav(b,"Settings");setContentView(s)}
    override fun onDestroy(){directClient?.cleanup();super.onDestroy()}
}
