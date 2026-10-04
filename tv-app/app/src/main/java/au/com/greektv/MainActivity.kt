package au.com.greektv

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Xml
import android.view.*
import android.widget.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class Channel(val name:String,val url:String,val group:String,val tvgId:String="")
class MainActivity:Activity(){
 private val bg=Color.rgb(2,7,13);private val card=Color.rgb(12,25,40);private val focus=Color.rgb(27,105,190);private val muted=Color.rgb(158,180,201);private val accent=Color.rgb(64,151,255)
 private val isPappas get()=packageName=="au.com.pappastv"
 private val brandName get()=if(isPappas)"PAPAS TV" else "RESKAKIS TV"
 private val placeName get()=if(isPappas)"Nafplio" else "Chios"
 private val placeUpper get()=placeName.uppercase()
 private val placeFilter get()=if(isPappas)"ΝΑΥΠΛΙΟ" else "ΧΙΟΣ"
 private var player:ExoPlayer?=null;private var previewPlayer:ExoPlayer?=null;private var channels=listOf<Channel>();private var current=0;private var overlay:TextView?=null;private var currentSection="LIVE TV"
 private var remoteConfig=JSONObject()
 private var remoteRefreshDone=false
 private var launchUpdateChecked=false
 private var epgLoading=false
 private val epgNow=mutableMapOf<String,String>()
 private val epgNext=mutableMapOf<String,String>()
 private lateinit var fav:Favourites
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  fav=Favourites(this)
  val cfgKey=if(isPappas)"papas_config" else "reskakis_config"
  try{remoteConfig=JSONObject(prefs.getString(cfgKey,"{}")?:"{}")}catch(_:Exception){}
  showHome()
  refreshRemoteConfig()
 }
 private val prefs by lazy{getSharedPreferences("greek_tv",MODE_PRIVATE)}
 override fun onStop(){super.onStop();player?.release();player=null;previewPlayer?.release();previewPlayer=null}
 private fun panel(c:Int,r:Float=22f)=GradientDrawable().apply{setColor(c);cornerRadius=r;setStroke(1,Color.argb(58,138,190,232))}
 private fun cfgString(key:String,default:String)=remoteConfig.optString(key,default).ifBlank{default}
 private fun refreshRemoteConfig(){
  if(remoteRefreshDone)return
  remoteRefreshDone=true
  Thread{try{
   val configUrl=if(isPappas)"https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/papas-config.json" else "https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/reskakis-config.json"
   val raw=URL(configUrl).openConnection().apply{connectTimeout=5000;readTimeout=7000}.getInputStream().bufferedReader().use{it.readText()}
   val obj=JSONObject(raw)
   val old=remoteConfig.toString()
   remoteConfig=obj
   prefs.edit().putString(if(isPappas)"papas_config" else "reskakis_config",raw).apply()
   runOnUiThread{
    if(old!=obj.toString()&&player==null&&previewPlayer==null)showHome()
    maybePromptLaunchUpdate()
   }
  }catch(_:Exception){}}.start()
 }
 private fun maybePromptLaunchUpdate(){
  if(launchUpdateChecked)return
  val latest=remoteConfig.optInt("latestVersionCode",currentVersionCode())
  if(latest<=currentVersionCode())return
  launchUpdateChecked=true
  val latestName=remoteConfig.optString("latestVersionName","new version")
  AlertDialog.Builder(this)
   .setTitle("Update available")
   .setMessage("$brandName $latestName is available. Update now?")
   .setPositiveButton("Update now"){_,_->openUri(cfgString("updateUrl",if(isPappas)"https://ptv.up.railway.app" else "https://rtv.up.railway.app"))}
   .setNegativeButton("Later",null)
   .show()
 }
 private fun currentVersionCode():Int=try{
  val p=packageManager.getPackageInfo(packageName,0)
  if(android.os.Build.VERSION.SDK_INT>=28)p.longVersionCode.toInt() else p.versionCode
 }catch(_:Exception){0}
 private fun showSettings(){
  val ver=try{packageManager.getPackageInfo(packageName,0).versionName}catch(_:Exception){"1.0"}
  AlertDialog.Builder(this).setTitle("$brandName Settings")
   .setMessage("Live content refreshes automatically.\n\nApp version $ver")
   .setPositiveButton("Check for update"){_,_->
    val latest=remoteConfig.optInt("latestVersionCode",currentVersionCode())
    if(latest>currentVersionCode())AlertDialog.Builder(this).setTitle("Update available").setMessage(brandName+" "+remoteConfig.optString("latestVersionName","new version")+" is ready.").setPositiveButton("Install"){_,_->openUri(cfgString("updateUrl",if(isPappas)"https://ptv.up.railway.app" else "https://rtv.up.railway.app"))}.setNegativeButton("Later",null).show()
    else Toast.makeText(this,"$brandName is up to date.",Toast.LENGTH_SHORT).show()
   }
   .setNeutralButton("Refresh content"){_,_->remoteRefreshDone=false;refreshRemoteConfig();Toast.makeText(this,"Refreshing $brandName content…",Toast.LENGTH_SHORT).show()}
   .setNegativeButton("Close",null).show()
 }
 private fun recordRecent(ch:Channel){
  try{
   val old=JSONArray(prefs.getString("recent_channels","[]")?:"[]")
   val arr=JSONArray()
   arr.put(JSONObject().put("name",ch.name).put("url",ch.url).put("group",ch.group))
   for(i in 0 until old.length()){
    val o=old.optJSONObject(i)?:continue
    if(o.optString("url")!=ch.url&&arr.length()<4)arr.put(o)
   }
   prefs.edit().putString("recent_channels",arr.toString()).apply()
  }catch(_:Exception){}
 }
 private fun playRecent(url:String){
  Thread{try{val all=parsePlaylist(try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e});runOnUiThread{channels=all;val i=all.indexOfFirst{it.url==url};if(i>=0)play(i)else loadChannels()}}catch(_:Exception){runOnUiThread{loadChannels()}}}.start()
 }
 private fun channelLogoUrl(ch:Channel):String{
  val logos=remoteConfig.optJSONObject("logos")
  val remote=logos?.optString(ch.tvgId,"")?:""
  if(remote.isNotBlank())return remote
  return when{
   ch.tvgId.startsWith("ERT1")->"https://i.imgur.com/UKbCtC1.png"
   ch.tvgId.startsWith("ANT1")->"https://i.imgur.com/ItxKvVS.png"
   ch.tvgId.startsWith("AlphaTV")->"https://i.imgur.com/6twzd38.png"
   ch.tvgId.startsWith("StarChannel")->"https://i.imgur.com/6NUpxhr.png"
   ch.tvgId.startsWith("OpenTV")->"https://upload.wikimedia.org/wikipedia/el/thumb/e/e5/Open_TV_logo.png/960px-Open_TV_logo.png"
   ch.tvgId.startsWith("MegaChannel")->"https://i.imgur.com/Z3k7iA0.png"
   else->""
  }
 }
 private fun loadImageInto(view:ImageView,url:String){
  if(url.isBlank())return
  Thread{try{val bmp=URL(url).openStream().use{BitmapFactory.decodeStream(it)};runOnUiThread{view.setImageBitmap(bmp)}}catch(_:Exception){}}.start()
 }
 private fun parseXmltvDate(v:String):Long=try{SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).parse(v.trim())?.time?:0L}catch(_:Exception){0L}
 private fun loadEpg(onDone:(()->Unit)?=null){
  if(epgLoading||channels.none{it.tvgId.isNotBlank()})return
  epgLoading=true
  val wanted=channels.map{it.tvgId}.filter{it.isNotBlank()}.toSet()
  Thread{try{
   val parser=Xml.newPullParser()
   val input=URL(cfgString("epgUrl","https://iptv-org.github.io/epg/guides/gr/cosmote.gr.epg.xml")).openConnection().apply{connectTimeout=7000;readTimeout=12000}.getInputStream()
   parser.setInput(input,"UTF-8")
   val now=System.currentTimeMillis()
   var event=parser.eventType
   while(event!=XmlPullParser.END_DOCUMENT){
    if(event==XmlPullParser.START_TAG&&parser.name=="programme"){
     val id=parser.getAttributeValue(null,"channel")?:""
     val start=parseXmltvDate(parser.getAttributeValue(null,"start")?:"")
     val stop=parseXmltvDate(parser.getAttributeValue(null,"stop")?:"")
     if(id in wanted){
      var title=""
      var inner=parser.next()
      while(!(inner==XmlPullParser.END_TAG&&parser.name=="programme")){
       if(inner==XmlPullParser.START_TAG&&parser.name=="title")title=parser.nextText()
       inner=parser.next()
      }
      if(title.isNotBlank()){
       if(start<=now&&stop>now)epgNow[id]=title
       else if(start>now&&!epgNext.containsKey(id))epgNext[id]=title
      }
     }
    }
    event=parser.next()
   }
   input.close()
  }catch(_:Exception){}finally{epgLoading=false;runOnUiThread{onDone?.invoke()}}}.start()
 }
 private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=21f;gravity=Gravity.CENTER_VERTICAL;isAllCaps=false;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.WHITE);background=panel(card);isFocusable=true;setPadding(30,0,24,0);stateListAnimator=null;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,72).apply{setMargins(0,5,0,5)};setOnFocusChangeListener{v,f->background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(10,116,213),Color.rgb(28,151,245))).apply{cornerRadius=18f;setStroke(2,Color.argb(205,255,255,255))}else panel(card);v.animate().scaleX(if(f)1.035f else 1f).scaleY(if(f)1.035f else 1f).setDuration(110).start();v.elevation=if(f)16f else 1f}}
 private fun shell(title:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(64,34,64,28);setBackgroundColor(bg);addView(TextView(this@MainActivity).apply{text=title;textSize=34f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.05f;setPadding(6,0,0,2)});addView(TextView(this@MainActivity).apply{text="Η Ελλάδα στο σπίτι σας  •  $placeUpper → WORLD";textSize=15f;setTextColor(accent);letterSpacing=.03f;setPadding(7,0,0,22)})}
 private fun showHome(){
  player?.release();player=null;previewPlayer?.release();previewPlayer=null
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

  val root=FrameLayout(this).apply{setBackgroundColor(bg)}
  val backdrop=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;alpha=.82f;setBackgroundColor(Color.rgb(2,8,15))}
  root.addView(backdrop,FrameLayout.LayoutParams(-1,-1))
  Thread{try{
   val hero=cfgString("heroUrl",if(isPappas)"https://commons.wikimedia.org/wiki/Special:Redirect/file/Nafplio_from_Palamidi_castle.jpg" else "https://commons.wikimedia.org/wiki/Special:Redirect/file/Sunset_at_%C3%87e%C5%9Fme_overlooking_Chios.jpg")
   val bmp=URL(hero).openStream().use{BitmapFactory.decodeStream(it)}
   runOnUiThread{backdrop.setImageBitmap(bmp)}
  }catch(_:Exception){}}.start()
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.argb(28,1,6,12),Color.argb(132,1,8,15),Color.argb(238,1,7,13)))},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,intArrayOf(Color.argb(118,246,106,28),Color.argb(42,248,149,65),Color.TRANSPARENT,Color.TRANSPARENT))},FrameLayout.LayoutParams(-1,230))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(120,0,8,18),Color.TRANSPARENT))},FrameLayout.LayoutParams(430,-1))

  val lowerVeil=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(95,1,8,15),Color.argb(185,1,7,13)))}
  root.addView(lowerVeil,FrameLayout.LayoutParams(-1,-1).apply{topMargin=150})
  val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,16,12)}

  // Full-width premium masthead, matching the locked reference.
  val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val identity=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  identity.addView(TextView(this).apply{
   text="🇬🇷";textSize=44f;gravity=Gravity.CENTER;background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(12,86,190),Color.rgb(8,55,132))).apply{cornerRadius=10f;setStroke(1,Color.argb(120,255,255,255))}
   elevation=8f
  },LinearLayout.LayoutParams(92,76).apply{setMargins(0,0,16,0)})
  val wordmark=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  wordmark.addView(TextView(this).apply{
   text=brandName;textSize=44f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.012f;setSingleLine(true);setShadowLayer(10f,0f,3f,Color.argb(110,0,0,0))
  })
  wordmark.addView(TextView(this).apply{
   text="GREEK TELEVISION  ·  $placeUpper  ·  AND MORE";textSize=10.5f;setTextColor(Color.rgb(235,240,247));letterSpacing=.10f;setSingleLine(true)
  })
  identity.addView(wordmark)
  top.addView(identity,LinearLayout.LayoutParams(0,-2,1f))
  top.addView(TextView(this).apply{
   text=cfgString("tagline",if(isPappas)"From Nafplio to the World" else "From Chios to the World").replace(" to the World","\nto the World");textSize=25f;typeface=Typeface.create("cursive",Typeface.ITALIC);setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(18,0,34,0);setShadowLayer(8f,0f,3f,Color.argb(120,0,0,0))
  })
  top.addView(TextView(this).apply{
   text=SimpleDateFormat("HH:mm   |   EEE d MMM",Locale.getDefault()).format(Date())+"   ⚙";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL or Gravity.END;setSingleLine(true)
  })
  page.addView(top,LinearLayout.LayoutParams(-1,124))

  val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}

  // Glass left rail.
  val nav=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(10,12,10,10)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(242,1,11,22),Color.argb(226,3,22,39))).apply{cornerRadius=16f;setStroke(1,Color.argb(72,150,195,230))}
   elevation=8f
  }
  val navItems=listOf<Pair<String,()->Unit>>(
   "⌂   Home" to {showHome()},"▣   Live TV" to {loadChannels()},"♥   Favourites" to {loadChannels(favouritesOnly=true)},"◷   Continue" to {loadLastChannel()},
   "▤   On Demand" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},"◉   $placeName" to {loadChannels(placeFilter)},"◎   World TV" to {loadChannels("ΔΙΕΘΝΗ")},
   "☷   Categories" to {loadChannels()},"▦   TV Guide" to {showTvGuide()},"⌕   Search" to {loadChannels()},"⚙   Settings" to {showSettings()}
  )
  navItems.forEachIndexed{i,it->
   nav.addView(button(it.first,it.second).apply{
    textSize=14f;setPadding(16,0,8,0);setSingleLine(true);layoutParams=LinearLayout.LayoutParams(-1,50).apply{setMargins(0,2,0,2)}
    background=if(i==0)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(11,129,231),Color.rgb(29,151,247))).apply{cornerRadius=12f;setStroke(1,Color.argb(180,255,255,255))} else panel(Color.argb(122,6,26,44),12f)
   })
  }
  body.addView(nav,LinearLayout.LayoutParams(224,-1).apply{setMargins(0,8,18,0)})

  val main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,0,0,0)}
  fun sectionTitle(t:String){
   main.addView(TextView(this).apply{text=t;textSize=24f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE);setPadding(0,9,0,6);setShadowLayer(6f,0f,2f,Color.argb(120,0,0,0))})
  }

  sectionTitle("Popular Greek Channels")
  val channelRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val channelTiles=listOf(
   arrayOf("ΕΡΤ 1","ERT 1 HD",Color.rgb(20,46,210).toString(),"0"),
   arrayOf("ERT 2","ERT 2 HD",Color.rgb(242,244,246).toString(),"1"),
   arrayOf("ANT1","ANT1 HD",Color.rgb(22,63,111).toString(),"0"),
   arrayOf("A","ALPHA HD",Color.rgb(229,28,48).toString(),"0"),
   arrayOf("ΣΚΑΪ","SKAI HD",Color.rgb(20,98,232).toString(),"0"),
   arrayOf("OPEN","OPEN HD",Color.rgb(7,18,31).toString(),"0"),
   arrayOf("MEGA","MEGA HD",Color.rgb(245,246,248).toString(),"1")
  )
  channelTiles.forEachIndexed{i,a->
   val action={if(i<2)loadChannels("ERT") else loadChannels()}
   channelRow.addView(
    logoCard(a[0],a[1],a[2].toInt(),a[3]=="1",action),
    LinearLayout.LayoutParams(0,138,1f).apply{setMargins(0,0,12,0)}
   )
  }
  main.addView(channelRow)

  sectionTitle("Continue Watching")
  val cont=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  run{
   val recent=try{JSONArray(prefs.getString("recent_channels","[]")?:"[]")}catch(_:Exception){JSONArray()}
   if(recent.length()==0){
    val cardView=imageCard("Start watching","Your recent channels will appear here","https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"){loadChannels()}
    cont.addView(cardView,LinearLayout.LayoutParams(0,182,1f).apply{setMargins(0,0,12,0)})
   }else{
    for(i in 0 until minOf(4,recent.length())){
     val o=recent.optJSONObject(i)?:continue
     val name=o.optString("name","Channel")
     val group=o.optString("group","Recently watched")
     val url=o.optString("url","")
     val image=when{
      group.contains("ERT",true)->"https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"
      group.contains("ΤΑΙΝ",true)->"https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1200&q=85"
      group.contains("ΜΟΥΣ",true)->"https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=85"
      else->"https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=85"
     }
     cont.addView(imageCard(name,"Recently watched",image){playRecent(url)},LinearLayout.LayoutParams(0,182,1f).apply{setMargins(0,0,12,0)})
    }
   }
  }
  main.addView(cont)

  sectionTitle("Browse by Category")
  val cats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val categoryData=listOf(
   arrayOf("▣  Greek TV","All Greek Channels",Color.rgb(18,124,210).toString()),
   arrayOf("◉  Movies","Greek & International",Color.rgb(155,26,83).toString()),
   arrayOf("▤  Series","Greek Series",Color.rgb(4,116,68).toString()),
   arrayOf("★  Kids","For the Little Ones",Color.rgb(225,124,5).toString()),
   arrayOf("◉  $placeName","Local Content",Color.rgb(6,132,153).toString()),
   arrayOf("◎  World TV","International Channels",Color.rgb(95,19,160).toString())
  )
  categoryData.forEachIndexed{i,a->
   val action=when(i){0->{ {loadChannels()} };1->{ {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")} };2->{ {showMessage("Series","More Greek series coming soon")} };3->{ {loadChannels("ΠΑΙΔΙΚΑ")} };4->{ {loadChannels(placeFilter)} };else->{ {loadChannels("ΔΙΕΘΝΗ")} }}
   cats.addView(tvCard(a[0],a[1],a[2].toInt(),action).apply{gravity=Gravity.CENTER_VERTICAL;elevation=4f},LinearLayout.LayoutParams(0,100,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(cats)

  sectionTitle("$placeName Highlights")
  val chios=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val chiosCards=listOf(
   arrayOf("$placeName Live","Local Content","https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=85"),
   arrayOf(if(isPappas)"Nafplio Old Town" else "Chios Villages","Explore","https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=1200&q=85"),
   arrayOf(if(isPappas)"Nafplio Coast" else "Chios Beaches",if(isPappas)"Seaside" else "Island Life","https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("$placeName Documentary","History","https://images.unsplash.com/photo-1530841377377-3ff06c0ca713?auto=format&fit=crop&w=1200&q=85"),
   arrayOf(if(isPappas)"Bourtzi" else "Chios Mastiha",if(isPappas)"Landmark" else "Tradition","https://images.unsplash.com/photo-1473093295043-cdd812d0e601?auto=format&fit=crop&w=1200&q=85")
  )
  chiosCards.forEach{a->
   chios.addView(imageCard(a[0],a[1],a[2]){loadChannels(placeFilter)},LinearLayout.LayoutParams(0,154,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(chios)

  body.addView(main,LinearLayout.LayoutParams(0,-1,1f))
  page.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(page)
  setContentView(root)
  nav.post{if(nav.childCount>0)nav.getChildAt(0).requestFocus()}
 }
 private fun popularLogoUrl(label:String):String{
  return when{
   label.startsWith("ERT 1")->"https://i.imgur.com/UKbCtC1.png"
   label.startsWith("ANT1")->"https://i.imgur.com/ItxKvVS.png"
   label.startsWith("ALPHA")->"https://i.imgur.com/6twzd38.png"
   label.startsWith("SKAI")->"https://i.imgur.com/mrKRFnf.png"
   label.startsWith("OPEN")->"https://upload.wikimedia.org/wikipedia/el/thumb/e/e5/Open_TV_logo.png/960px-Open_TV_logo.png"
   label.startsWith("MEGA")->"https://i.imgur.com/Z3k7iA0.png"
   else->""
  }
 }
 private fun logoCard(mark:String,label:String,base:Int,darkText:Boolean=false,action:()->Unit):FrameLayout{
  val frame=FrameLayout(this).apply{
   isFocusable=true;isClickable=true;elevation=6f;clipToOutline=true
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,if(darkText)Color.rgb(222,226,232) else Color.rgb(5,18,33))).apply{cornerRadius=14f;setStroke(1,Color.argb(105,170,205,235))}
  }
  val logoUrl=popularLogoUrl(label)
  if(logoUrl.isNotBlank()){
   val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(14,12,14,12)}
   frame.addView(logo,FrameLayout.LayoutParams(-1,96))
   loadImageInto(logo,logoUrl)
  }else{
   val logo=TextView(this).apply{
    text=mark;textSize=27f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);gravity=Gravity.CENTER
    setTextColor(if(darkText)Color.rgb(23,50,130) else Color.WHITE);setShadowLayer(if(darkText)0f else 5f,0f,2f,Color.argb(120,0,0,0))
   }
   frame.addView(logo,FrameLayout.LayoutParams(-1,96))
  }
  val cap=TextView(this).apply{
   text=label;textSize=12f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
   background=GradientDrawable().apply{setColor(Color.argb(220,1,10,20));cornerRadii=floatArrayOf(0f,0f,0f,0f,14f,14f,14f,14f)}
   setPadding(4,3,4,4)
  }
  frame.addView(cap,FrameLayout.LayoutParams(-1,34,Gravity.BOTTOM))
  frame.setOnClickListener{action()}
  frame.setOnFocusChangeListener{v,f->
   v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(4,Color.WHITE);cornerRadius=14f}else null
   v.animate().scaleX(if(f)1.055f else 1f).scaleY(if(f)1.055f else 1f).translationZ(if(f)8f else 0f).setDuration(105).start();v.elevation=if(f)20f else 6f
  }
  return frame
 }
 private fun tvCard(title:String,subtitle:String="",base:Int=card,action:()->Unit):LinearLayout{
  return LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM;isFocusable=true;isClickable=true
   setPadding(14,10,14,10)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,Color.rgb(7,18,31))).apply{cornerRadius=14f;setStroke(1,Color.argb(90,150,195,230))}
   addView(TextView(this@MainActivity).apply{text=title;textSize=17f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
   if(subtitle.isNotBlank())addView(TextView(this@MainActivity).apply{text=subtitle;textSize=12f;setTextColor(Color.rgb(218,228,238));setPadding(0,2,0,0)})
   setOnClickListener{action()}
   setOnFocusChangeListener{v,f->
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(if(f)focus else base,Color.rgb(6,19,34))).apply{cornerRadius=14f;setStroke(if(f)3 else 1,if(f)Color.WHITE else Color.argb(90,150,195,230))}
    v.animate().scaleX(if(f)1.055f else 1f).scaleY(if(f)1.055f else 1f).setDuration(110).start();v.elevation=if(f)18f else 2f
   }
  }
 }
 private fun imageCard(title:String,subtitle:String,url:String,action:()->Unit):FrameLayout{
  val frame=FrameLayout(this).apply{isFocusable=true;isClickable=true;background=panel(Color.rgb(8,20,34),16f);elevation=5f;clipToOutline=true}
  val img=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.rgb(16,30,44))}
  frame.addView(img,FrameLayout.LayoutParams(-1,-1))
  Thread{try{val bmp=URL(url).openStream().use{BitmapFactory.decodeStream(it)};runOnUiThread{img.setImageBitmap(bmp)}}catch(_:Exception){}}.start()
  val shade=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(Color.argb(242,2,8,14),Color.argb(118,2,8,14),Color.argb(24,2,8,14)))}
  frame.addView(shade,FrameLayout.LayoutParams(-1,-1))
  val textWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8)}
  textWrap.addView(TextView(this).apply{text=title;textSize=16f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);setShadowLayer(6f,0f,2f,Color.BLACK)})
  textWrap.addView(TextView(this).apply{text=subtitle;textSize=11f;setTextColor(Color.rgb(230,237,244))})
  frame.addView(textWrap,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  frame.setOnClickListener{action()}
  frame.setOnFocusChangeListener{v,f->v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(4,Color.WHITE);cornerRadius=14f}else null;v.animate().scaleX(if(f)1.05f else 1f).scaleY(if(f)1.05f else 1f).translationZ(if(f)7f else 0f).setDuration(105).start();v.elevation=if(f)18f else 3f}
  return frame
 }
 private fun showTvGuide(){
  player?.release();player=null;previewPlayer?.release();previewPlayer=null
  currentSection="TV GUIDE"
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(42,28,42,28)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(4,20,35),Color.rgb(2,8,15)))
  }
  val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val titleWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleWrap.addView(TextView(this).apply{text="TV GUIDE";textSize=34f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)})
  titleWrap.addView(TextView(this).apply{text="$brandName  •  NOW & NEXT";textSize=12f;setTextColor(accent);letterSpacing=.055f})
  header.addView(titleWrap,LinearLayout.LayoutParams(0,-2,1f))
  header.addView(TextView(this).apply{text="OK Watch   •   BACK Home";textSize=14f;setTextColor(muted)})
  root.addView(header,LinearLayout.LayoutParams(-1,78))

  val status=TextView(this).apply{text="Loading TV guide…";textSize=14f;setTextColor(Color.rgb(194,214,232));setPadding(14,8,14,8);background=GradientDrawable().apply{setColor(Color.argb(105,18,55,84));cornerRadius=12f;setStroke(1,Color.argb(70,120,180,230))}}
  root.addView(status,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,4,0,14)})

  val scroll=ScrollView(this)
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  scroll.addView(list)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)

  fun render(){
   list.removeAllViews()
   val guideChannels=channels.filter{it.tvgId.isNotBlank()}.ifEmpty{channels}
   status.text=if(epgNow.isEmpty()&&epgNext.isEmpty())"Programme data unavailable for some channels • Live channels still selectable" else "Live programme information"
   guideChannels.forEachIndexed{i,ch->
    val row=LinearLayout(this).apply{
     orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
     setPadding(16,10,16,10)
     background=panel(Color.rgb(10,31,50),12f)
    }
    val logoUrl=channelLogoUrl(ch)
    if(logoUrl.isNotBlank()){
     val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(4,4,4,4);background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=9f}}
     row.addView(logo,LinearLayout.LayoutParams(52,40).apply{setMargins(0,0,14,0)})
     loadImageInto(logo,logoUrl)
    }else{
     row.addView(TextView(this).apply{text="TV";gravity=Gravity.CENTER;textSize=12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(20,95,180));cornerRadius=9f}},LinearLayout.LayoutParams(52,40).apply{setMargins(0,0,14,0)})
    }
    val name=TextView(this).apply{text=ch.name;textSize=17f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setSingleLine(true)}
    row.addView(name,LinearLayout.LayoutParams(220,-2).apply{setMargins(0,0,18,0)})
    val nowTitle=epgNow[ch.tvgId]?:"Live programming"
    val nextTitle=epgNext[ch.tvgId]?:"Schedule unavailable"
    val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    info.addView(TextView(this@MainActivity).apply{text="NOW  •  $nowTitle";textSize=15f;setTextColor(Color.WHITE);setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END})
    info.addView(TextView(this@MainActivity).apply{text="NEXT •  $nextTitle";textSize=12f;setTextColor(Color.rgb(150,186,215));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,4,0,0)})
    row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
    row.setOnClickListener{val realIndex=channels.indexOf(ch);if(realIndex>=0)play(realIndex)}
    row.setOnFocusChangeListener{v,focusOn->
     v.background=if(focusOn)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,104,198),Color.rgb(34,155,246))).apply{cornerRadius=12f;setStroke(2,Color.argb(225,255,255,255))} else panel(Color.rgb(10,31,50),12f)
     v.animate().scaleX(if(focusOn)1.012f else 1f).scaleY(if(focusOn)1.012f else 1f).setDuration(90).start()
    }
    list.addView(row,LinearLayout.LayoutParams(-1,74).apply{setMargins(0,0,0,8)})
   }
   list.post{if(list.childCount>0)list.getChildAt(0).requestFocus()}
  }

  Thread{try{
   val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e}
   val all=parsePlaylist(txt)
   runOnUiThread{
    channels=all
    render()
    loadEpg{render()}
   }
  }catch(_:Exception){runOnUiThread{status.text="Unable to load TV guide";render()}}}.start()
 }
 private fun loadLastChannel(){val u=prefs.getString("last_channel",null);if(u==null){loadChannels();return};Thread{try{val all=parsePlaylist(fetchPlaylist());runOnUiThread{channels=all;val i=all.indexOfFirst{it.url==u};if(i>=0)play(i)else showList()}}catch(e:Exception){runOnUiThread{loadChannels()}}}.start()}
 private fun fetchPlaylist():String{val u=URL("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u");val c=u.openConnection().apply{connectTimeout=8000;readTimeout=12000};return c.getInputStream().bufferedReader().use{it.readText()}.also{prefs.edit().putString("playlist_cache",it).apply()}}
 private fun parsePlaylist(txt:String):List<Channel>{val out=mutableListOf<Channel>();var n="";var g="";var id="";txt.lines().forEach{l->if(l.startsWith("#EXTINF")){n=l.substringAfterLast(",").trim();g=l.substringAfter("group-title=\"", "").substringBefore("\"", "");id=l.substringAfter("tvg-id=\"", "").substringBefore("\"", "")}else if(l.startsWith("http")&&n.isNotBlank()){out.add(Channel(n,l.trim(),g,id));n="";g="";id=""}};return out}
 private fun loadChannels(filter:String?=null,favouritesOnly:Boolean=false){currentSection=when{favouritesOnly->"FAVOURITES";filter?.contains(placeFilter,true)==true->placeUpper;filter?.contains("ΠΑΙΔΙΚΑ",true)==true->"KIDS";filter?.contains("ΤΑΙΝΙΕΣ",true)==true->"ON DEMAND";filter?.contains("ΔΙΕΘΝΗ",true)==true->"WORLD TV";filter?.contains("ERT",true)==true->"ERT";else->"LIVE TV"};Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val out=parsePlaylist(txt).filter{(filter==null||it.group.contains(filter,true))&&(!favouritesOnly||fav.has(it.url))};runOnUiThread{channels=out;if(out.isEmpty()){showMessage(brandName,if(favouritesOnly)"No favourites yet." else "No channels found.")}else{showList()}}}catch(e:Exception){runOnUiThread{showMessage(brandName,"Unable to load right now. Check the internet connection and try again.")}}}.start()}
 private fun showList(){
  previewPlayer?.release();previewPlayer=null
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(42,28,42,28);background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(4,20,35),Color.rgb(2,8,15)))}

  val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val titleWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleWrap.addView(TextView(this).apply{text=currentSection;textSize=34f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.015f;setShadowLayer(7f,0f,2f,Color.argb(100,0,0,0))})
  titleWrap.addView(TextView(this).apply{text="$brandName  •  GREEK TELEVISION  •  $placeUpper TO THE WORLD";textSize=12f;setTextColor(accent);letterSpacing=.055f})
  header.addView(titleWrap,LinearLayout.LayoutParams(0,-2,1f))
  header.addView(TextView(this).apply{text="▲▼ Browse   •   OK Full Screen   •   ★ Favourite";textSize=14f;setTextColor(muted);gravity=Gravity.END})
  root.addView(header,LinearLayout.LayoutParams(-1,76))

  val content=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}

  val listPane=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(4,17,30),Color.rgb(8,29,48))).apply{cornerRadius=18f;setStroke(1,Color.argb(88,120,180,230))};elevation=7f
   setPadding(12,12,12,12)
  }
  val scroll=ScrollView(this)
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val previewTitle=TextView(this).apply{text="Select a channel";textSize=27f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)}
  val previewMeta=TextView(this).apply{text="Live preview";textSize=14f;setTextColor(accent);setPadding(0,5,0,10)}

  val previewPane=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(18,0,0,0)
  }
  val videoFrame=FrameLayout(this).apply{
   background=GradientDrawable().apply{setColor(Color.BLACK);cornerRadius=18f;setStroke(2,Color.argb(125,120,185,235))};clipToOutline=true;elevation=9f
  }
  val playerView=PlayerView(this).apply{
   useController=false
   keepScreenOn=true
   setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
   setBackgroundColor(Color.BLACK)
  }
  videoFrame.addView(playerView,FrameLayout.LayoutParams(-1,-1))
  val previewStatus=TextView(this).apply{
   text="Preview unavailable • OK to try full screen"
   textSize=13f
   setTextColor(Color.WHITE)
   gravity=Gravity.CENTER
   visibility=View.GONE
   background=GradientDrawable().apply{setColor(Color.argb(220,5,18,31));cornerRadius=12f}
   setPadding(18,10,18,10)
  }
  videoFrame.addView(previewStatus,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
  videoFrame.addView(TextView(this).apply{
   text="LIVE";textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER
   background=GradientDrawable().apply{setColor(Color.rgb(205,34,52));cornerRadius=10f}
   setPadding(14,4,14,4)
  },FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply{setMargins(16,16,0,0)})
  previewPane.addView(videoFrame,LinearLayout.LayoutParams(-1,0,1f))
  previewPane.addView(previewTitle,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,16,0,0)})
  previewPane.addView(previewMeta)
  previewPane.addView(TextView(this).apply{
   text="OK  Full Screen   •   ▲▼  Change Channel"
   textSize=13f;setTextColor(muted);setPadding(0,2,0,0)
  })

  fun startPreview(index:Int){
   if(index !in channels.indices)return
   current=index
   previewTitle.text=channels[index].name
   val guideNow=epgNow[channels[index].tvgId]
   val guideNext=epgNext[channels[index].tvgId]
   previewMeta.text=if(guideNow!=null)"NOW  •  $guideNow"+(if(guideNext!=null)"\nNEXT •  $guideNext" else "") else (if(channels[index].group.isBlank())"GREEK TV" else channels[index].group.uppercase())+"   •   LIVE NOW"
   previewStatus.visibility=View.GONE
   previewPlayer?.release()
   previewPlayer=ExoPlayer.Builder(this).build().also{p->
    playerView.player=p
    p.volume=0f
    p.addListener(object:Player.Listener{
     override fun onPlaybackStateChanged(state:Int){
      if(state==Player.STATE_READY && previewPlayer===p)previewStatus.visibility=View.GONE
     }
     override fun onPlayerError(error:PlaybackException){
      if(previewPlayer===p)previewStatus.visibility=View.VISIBLE
     }
    })
    p.setMediaItem(MediaItem.fromUri(channels[index].url))
    p.prepare()
    p.play()
    playerView.postDelayed({
     if(previewPlayer===p && p.playbackState!=Player.STATE_READY)previewStatus.visibility=View.VISIBLE
    },8000)
   }
  }

  channels.forEachIndexed{i,ch->
   val row=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
    setPadding(16,0,14,0)
    background=panel(Color.rgb(10,31,50),12f)
    layoutParams=LinearLayout.LayoutParams(-1,72).apply{setMargins(0,0,0,8)}
   }
   val logoUrl=channelLogoUrl(ch)
   if(logoUrl.isNotBlank()){
    val badge=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(3,3,3,3);background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=10f}}
    row.addView(badge,LinearLayout.LayoutParams(46,36).apply{setMargins(0,0,14,0)})
    loadImageInto(badge,logoUrl)
   }else{
    val badge=TextView(this).apply{
     text=when{
      ch.name.contains("ERT",true)->"ERT"
      ch.name.contains("ALPHA",true)->"A"
      ch.name.contains("SKAI",true)||ch.name.contains("ΣΚΑΪ",true)->"Σ"
      ch.name.contains("MEGA",true)->"M"
      else->"TV"
     }
     textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER
     background=GradientDrawable().apply{setColor(Color.rgb(20,95,180));cornerRadius=10f}
    }
    row.addView(badge,LinearLayout.LayoutParams(46,36).apply{setMargins(0,0,14,0)})
   }
   val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=52}
   info.addView(TextView(this).apply{text=ch.name;textSize=17f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.WHITE);setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;maxLines=1})
   info.addView(TextView(this).apply{text=if(ch.group.isBlank())"LIVE TV" else ch.group.uppercase();textSize=10f;setTextColor(Color.rgb(145,177,205));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;maxLines=1;setPadding(0,2,0,0)})
   row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
   row.addView(TextView(this).apply{text=if(fav.has(ch.url))"★" else "›";textSize=20f;setTextColor(if(fav.has(ch.url))Color.rgb(255,203,62) else Color.LTGRAY);gravity=Gravity.CENTER})
   row.setOnClickListener{previewPlayer?.release();previewPlayer=null;play(i)}
   row.setOnLongClickListener{val added=fav.toggle(ch.url);Toast.makeText(this,if(added)"★ Added to favourites" else "Removed from favourites",Toast.LENGTH_SHORT).show();showList();true}
   row.setOnFocusChangeListener{v,hasFocus->
    v.background=if(hasFocus)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,104,198),Color.rgb(34,155,246))).apply{cornerRadius=12f;setStroke(2,Color.argb(220,255,255,255))} else panel(Color.rgb(10,31,50),12f)
    v.animate().scaleX(if(hasFocus)1.015f else 1f).scaleY(if(hasFocus)1.015f else 1f).setDuration(90).start()
    if(hasFocus)startPreview(i)
   }
   list.addView(row)
  }

  scroll.addView(list)
  listPane.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  content.addView(listPane,LinearLayout.LayoutParams(0,-1,.40f).apply{setMargins(0,0,18,0)})
  content.addView(previewPane,LinearLayout.LayoutParams(0,-1,.60f))
  root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  loadEpg()
  list.post{if(list.childCount>0)list.getChildAt(0).requestFocus()}
 }
 private fun play(i:Int){previewPlayer?.release();previewPlayer=null;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;current=i;prefs.edit().putString("last_channel",channels[i].url).apply();recordRecent(channels[i]);player?.release();player=ExoPlayer.Builder(this).build();player!!.addListener(object:Player.Listener{override fun onPlayerError(error:PlaybackException){runOnUiThread{Toast.makeText(this@MainActivity,"Το κανάλι δεν είναι διαθέσιμο. Δοκιμάστε άλλο.",Toast.LENGTH_LONG).show();showList()}}});val frame=FrameLayout(this);val v=PlayerView(this).apply{player=this@MainActivity.player;useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);keepScreenOn=true;controllerShowTimeoutMs=3000;setBackgroundColor(Color.BLACK)};frame.addView(v,FrameLayout.LayoutParams(-1,-1));overlay=TextView(this).apply{text="●  LIVE   "+channels[i].name;textSize=20f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(235,7,24,41),Color.argb(215,12,50,82))).apply{cornerRadius=14f;setStroke(1,Color.argb(95,180,215,245))};setPadding(26,14,30,14);elevation=10f};frame.addView(overlay,FrameLayout.LayoutParams(-2,-2,Gravity.START or Gravity.BOTTOM).apply{setMargins(42,0,0,42)});setContentView(frame);overlay?.postDelayed({overlay?.visibility=View.GONE},2600);player!!.setMediaItem(MediaItem.fromUri(channels[i].url));player!!.prepare();player!!.play()}
 override fun onKeyDown(k:Int,e:KeyEvent?):Boolean{if(player!=null&&channels.isNotEmpty()){when(k){KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_CHANNEL_UP->{play((current+1)%channels.size);return true};KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_CHANNEL_DOWN->{play((current-1+channels.size)%channels.size);return true};KeyEvent.KEYCODE_STAR,KeyEvent.KEYCODE_BOOKMARK->{fav.toggle(channels[current].url);Toast.makeText(this,if(fav.has(channels[current].url))"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();return true};KeyEvent.KEYCODE_BACK->{player?.release();player=null;showList();return true}}};return super.onKeyDown(k,e)}
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage(brandName,"Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
