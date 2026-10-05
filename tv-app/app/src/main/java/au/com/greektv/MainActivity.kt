package au.com.greektv

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Xml
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
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
 private val brandName get()=if(isPappas)"PAPAS TV" else "GREEK ONE"
 private val placeName get()=if(isPappas)"Nafplio" else "Chios"
 private val placeUpper get()=placeName.uppercase()
 private val placeFilter get()=if(isPappas)"ΝΑΥΠΛΙΟ" else "ΧΙΟΣ"
 private var player:ExoPlayer?=null;private var previewPlayer:ExoPlayer?=null;private var channels=listOf<Channel>();private var current=0;private var overlay:TextView?=null;private var miniGuide:View?=null;private var currentSection="LIVE TV"
 private var remoteConfig=JSONObject()
 private var remoteRefreshDone=false
 private var launchUpdateChecked=false
 private var screenMode="HOME"
 private var activeHomeNav="Home"
 private var epgLoading=false
 private var epgLoadedAt=0L
 private val epgCacheTtlMs=10*60*1000L
 private val previewHandler=Handler(Looper.getMainLooper())
 private val headerHandler=Handler(Looper.getMainLooper())
 private var athensInfoView:TextView?=null
 private var sydneyInfoView:TextView?=null
 private var dateInfoView:TextView?=null
 private var athensTemp="--"
 private var athensCondition="Weather"
 private var sydneyTemp="--"
 private var sydneyCondition="Weather"
 private var weatherLoadedAt=0L
 private var homeBackdropIndex=0
 private val homeBackdropUrls=listOf(
  "https://commons.wikimedia.org/wiki/Special:Redirect/file/Sunset_at_%C3%87e%C5%9Fme_overlooking_Chios.jpg",
  "https://commons.wikimedia.org/wiki/Special:Redirect/file/Chios_town_view.jpg",
  "https://commons.wikimedia.org/wiki/Special:Redirect/file/Mesta_Chios_Greece.jpg",
  "https://commons.wikimedia.org/wiki/Special:Redirect/file/Pyrgi_Chios_Greece.jpg"
 )
 private val weatherCacheTtlMs=30*60*1000L
 private val imageCache=object:LruCache<String,Bitmap>(24){}
 private val epgNow=mutableMapOf<String,String>()
 private val epgNext=mutableMapOf<String,String>()
 private lateinit var fav:Favourites
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  fav=Favourites(this)
  val cfgKey=if(isPappas)"papas_config" else "reskakis_config"
  try{remoteConfig=JSONObject(prefs.getString(cfgKey,"{}")?:"{}")}catch(_:Exception){}
  showLaunchScreen()
  refreshRemoteConfig()
  Handler(Looper.getMainLooper()).postDelayed({if(player==null)showHome()},650)
 }
 private val prefs by lazy{getSharedPreferences("greek_tv",MODE_PRIVATE)}
 override fun onStop(){super.onStop();previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null);player?.release();player=null;previewPlayer?.release();previewPlayer=null}
 private fun panel(c:Int,r:Float=22f)=GradientDrawable().apply{setColor(c);cornerRadius=r;setStroke(1,Color.argb(58,138,190,232))}
 private fun showLaunchScreen(){
  val root=FrameLayout(this).apply{setBackgroundColor(Color.rgb(2,7,13))}
  val glow=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(3,29,56),Color.rgb(5,62,115),Color.rgb(2,7,13)))}
  root.addView(glow,FrameLayout.LayoutParams(-1,-1))
  val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
  if(isPappas){
   wrap.addView(TextView(this).apply{text="🇬🇷";textSize=58f;gravity=Gravity.CENTER})
   wrap.addView(TextView(this).apply{text=brandName;textSize=46f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER;letterSpacing=.035f})
  }else{
   wrap.addView(ImageView(this).apply{setImageResource(R.drawable.greek_one_mark);scaleType=ImageView.ScaleType.CENTER_INSIDE},LinearLayout.LayoutParams(360,360))
  }
  wrap.addView(TextView(this).apply{text="GREEK TELEVISION  •  $placeUpper  •  AND MORE";textSize=13f;setTextColor(Color.rgb(175,211,241));gravity=Gravity.CENTER;letterSpacing=.08f;setPadding(0,10,0,0)})
  root.addView(wrap,FrameLayout.LayoutParams(-1,-1))
  setContentView(root)
 }
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
   .setPositiveButton("Update now"){_,_->installAppUpdate(cfgString("updateUrl",if(isPappas)"https://ptv.up.railway.app" else "https://rtv.up.railway.app"))}
   .setNegativeButton("Later",null)
   .show()
 }
 private fun currentVersionCode():Int=try{
  val p=packageManager.getPackageInfo(packageName,0)
  if(android.os.Build.VERSION.SDK_INT>=28)p.longVersionCode.toInt() else p.versionCode
 }catch(_:Exception){0}
 private fun showSettings(){
  if(isPappas){
   val ver=try{packageManager.getPackageInfo(packageName,0).versionName}catch(_:Exception){"1.0"}
   AlertDialog.Builder(this).setTitle("$brandName Settings")
    .setMessage("Live content refreshes automatically.\n\nApp version $ver")
    .setPositiveButton("Check for update"){_,_->
     val latest=remoteConfig.optInt("latestVersionCode",currentVersionCode())
     if(latest>currentVersionCode())AlertDialog.Builder(this).setTitle("Update available").setMessage(brandName+" "+remoteConfig.optString("latestVersionName","new version")+" is ready.").setPositiveButton("Install"){_,_->installAppUpdate(cfgString("updateUrl","https://ptv.up.railway.app"))}.setNegativeButton("Later",null).show()
     else Toast.makeText(this,"$brandName is up to date.",Toast.LENGTH_SHORT).show()
    }
    .setNeutralButton("Refresh content"){_,_->remoteRefreshDone=false;refreshRemoteConfig();Toast.makeText(this,"Refreshing $brandName content…",Toast.LENGTH_SHORT).show()}
    .setNegativeButton("Close",null).show()
   return
  }
  screenMode="SETTINGS"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  val ver=try{packageManager.getPackageInfo(packageName,0).versionName}catch(_:Exception){"1.0"}
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(54,34,54,30)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(5,26,45),Color.rgb(2,9,17)))
  }
  val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val titleWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleWrap.addView(TextView(this).apply{text="Settings";textSize=34f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)})
  titleWrap.addView(TextView(this).apply{text="Greek One preferences and app status";textSize=13f;setTextColor(Color.rgb(130,191,232));letterSpacing=.04f})
  head.addView(titleWrap,LinearLayout.LayoutParams(0,-2,1f))
  head.addView(button("←  Home"){activeHomeNav="Home";showHome()},LinearLayout.LayoutParams(180,58))
  root.addView(head,LinearLayout.LayoutParams(-1,84))
  fun settingCard(title:String,subtitle:String,action:()->Unit):LinearLayout=
   LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true;setPadding(24,16,24,16)
    background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(240,6,28,48),Color.argb(220,8,47,77))).apply{cornerRadius=18f;setStroke(1,Color.argb(90,135,198,240))}
    val tw=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL}
    tw.addView(TextView(this@MainActivity).apply{text=title;textSize=20f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE)})
    tw.addView(TextView(this@MainActivity).apply{text=subtitle;textSize=12.5f;setTextColor(Color.rgb(170,202,226));setPadding(0,4,0,0)})
    addView(tw,LinearLayout.LayoutParams(0,-2,1f))
    addView(TextView(this@MainActivity).apply{text="›";textSize=32f;setTextColor(Color.rgb(88,194,255));gravity=Gravity.CENTER},LinearLayout.LayoutParams(48,48))
    setOnClickListener{action()}
    setOnFocusChangeListener{v,f->
     background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,103,197),Color.rgb(28,170,241))).apply{cornerRadius=18f;setStroke(2,Color.WHITE)}
     else GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(240,6,28,48),Color.argb(220,8,47,77))).apply{cornerRadius=18f;setStroke(1,Color.argb(90,135,198,240))}
     v.animate().scaleX(if(f)1.018f else 1f).scaleY(if(f)1.018f else 1f).setDuration(145).start()
    }
   }
  root.addView(settingCard("Check for update","See whether a newer Greek One build is available"){
   val latest=remoteConfig.optInt("latestVersionCode",currentVersionCode())
   if(latest>currentVersionCode())AlertDialog.Builder(this).setTitle("Update available").setMessage("Greek One "+remoteConfig.optString("latestVersionName","new version")+" is ready.").setPositiveButton("Install"){_,_->installAppUpdate(cfgString("updateUrl","https://rtv.up.railway.app"))}.setNegativeButton("Later",null).show()
   else Toast.makeText(this,"Greek One is up to date.",Toast.LENGTH_SHORT).show()
  },LinearLayout.LayoutParams(-1,92).apply{setMargins(0,8,0,14)})
  root.addView(settingCard("Refresh content","Reload channels, guide configuration and branding"){
   prefs.edit().remove("playlist_cache").putLong("playlist_cache_at",0L).apply();remoteRefreshDone=false;refreshRemoteConfig()
   Toast.makeText(this,"Refreshing Greek One content…",Toast.LENGTH_SHORT).show()
  },LinearLayout.LayoutParams(-1,92).apply{setMargins(0,0,0,14)})
  root.addView(settingCard("Playback & TV","Live streams use the TV-native Media3 player"){
   Toast.makeText(this,"Playback is optimized for Android TV.",Toast.LENGTH_SHORT).show()
  },LinearLayout.LayoutParams(-1,92).apply{setMargins(0,0,0,14)})
  val info=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(22,18,22,18)
   background=GradientDrawable().apply{setColor(Color.argb(155,4,18,31));cornerRadius=18f;setStroke(1,Color.argb(65,120,180,225))}
  }
  info.addView(TextView(this).apply{text="ABOUT GREEK ONE";textSize=11f;letterSpacing=.12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(104,180,227))})
  info.addView(TextView(this).apply{text="Greek television, live channels, guide, favourites, recent viewing and YouTube discovery in one TV-first experience.";textSize=14f;setTextColor(Color.rgb(220,232,242));setPadding(0,9,0,0)})
  root.addView(info,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,0)})
  root.addView(TextView(this).apply{text="Greek One  •  Version $ver  •  Build \${currentVersionCode()}";textSize=10f;letterSpacing=.08f;gravity=Gravity.CENTER_HORIZONTAL;setTextColor(Color.rgb(91,137,170));setPadding(0,24,0,0)})
  setContentView(root)
  root.post{if(root.childCount>1)root.getChildAt(1).requestFocus()}
 }
 private fun recordRecent(ch:Channel){
  try{
   val old=JSONArray(prefs.getString("recent_channels","[]")?:"[]")
   val arr=JSONArray()
   arr.put(JSONObject().put("name",ch.name).put("url",ch.url).put("group",ch.group).put("tvgId",ch.tvgId).put("watchedAt",System.currentTimeMillis()))
   for(i in 0 until old.length()){
    val o=old.optJSONObject(i)?:continue
    if(o.optString("url")!=ch.url&&arr.length()<4)arr.put(o)
   }
   prefs.edit().putString("recent_channels",arr.toString()).apply()
  }catch(_:Exception){}
 }
 private fun watchedAgo(ts:Long):String{
  if(ts<=0L)return "Recently watched"
  val mins=((System.currentTimeMillis()-ts)/60000L).coerceAtLeast(0L)
  return when{mins<1->"Watched just now";mins<60->"Watched $mins min ago";mins<1440->"Watched ${mins/60} hr ago";else->"Watched ${mins/1440} d ago"}
 }
 private fun homeCachedChannels():List<Channel> = try{prefs.getString("playlist_cache",null)?.let{parsePlaylist(it)}?:emptyList()}catch(_:Exception){emptyList()}
 private fun homeChannel(label:String):Channel?{
  val key=label.replace(" HD","").replace("ΕΡΤ","ERT").replace("ΣΚΑΪ","SKAI")
  return homeCachedChannels().firstOrNull{it.name.replace("ΕΡΤ","ERT").replace("ΣΚΑΪ","SKAI").contains(key,true)}
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
  imageCache.get(url)?.let{view.setImageBitmap(it);return}
  Thread{try{
   val bmp=URL(url).openStream().use{BitmapFactory.decodeStream(it)}
   if(bmp!=null)imageCache.put(url,bmp)
   runOnUiThread{if(bmp!=null)view.setImageBitmap(bmp)}
  }catch(_:Exception){}}.start()
 }
 private fun parseXmltvDate(v:String):Long=try{SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).parse(v.trim())?.time?:0L}catch(_:Exception){0L}
 private fun loadEpg(onDone:(()->Unit)?=null){
  if(channels.none{it.tvgId.isNotBlank()})return
  val nowMs=System.currentTimeMillis()
  if(epgNow.isNotEmpty()&&nowMs-epgLoadedAt<epgCacheTtlMs){onDone?.invoke();return}
  if(epgLoading)return
  epgLoading=true
  epgNow.clear();epgNext.clear()
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
  }catch(_:Exception){}finally{epgLoadedAt=System.currentTimeMillis();epgLoading=false;runOnUiThread{onDone?.invoke()}}}.start()
 }
 private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=21f;gravity=Gravity.CENTER_VERTICAL;isAllCaps=false;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.WHITE);background=panel(card);isFocusable=true;setPadding(30,0,24,0);stateListAnimator=null;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,72).apply{setMargins(0,5,0,5)};setOnFocusChangeListener{v,f->background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(10,116,213),Color.rgb(28,151,245))).apply{cornerRadius=18f;setStroke(2,Color.argb(205,255,255,255))}else panel(card);v.animate().scaleX(if(f)1.035f else 1f).scaleY(if(f)1.035f else 1f).setDuration(145).start();v.elevation=if(f)16f else 1f}}
 private fun weatherName(code:Int)=when(code){
  0->"Clear";1,2->"Mostly clear";3->"Cloudy";45,48->"Fog";51,53,55,56,57->"Drizzle";61,63,65,66,67,80,81,82->"Rain";71,73,75,77,85,86->"Snow";95,96,99->"Storm";else->"Weather"
 }
 private fun timeAt(zone:String):String=SimpleDateFormat("hh:mm a",Locale.US).apply{timeZone=TimeZone.getTimeZone(zone)}.format(Date())
 private fun updateHomeHeader(){
  athensInfoView?.text="🇬🇷  ATHENS\n${timeAt("Europe/Athens")}  •  $athensTemp°C  •  $athensCondition"
  sydneyInfoView?.text="🇦🇺  SYDNEY\n${timeAt("Australia/Sydney")}  •  $sydneyTemp°C  •  $sydneyCondition"
  dateInfoView?.text=SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(Date()).uppercase()
 }
 private val headerTick=object:Runnable{override fun run(){if(screenMode=="HOME"){updateHomeHeader();headerHandler.postDelayed(this,30000)}}}
 private fun refreshHomeWeather(){
  if(isPappas)return
  val now=System.currentTimeMillis()
  if(now-weatherLoadedAt<weatherCacheTtlMs){updateHomeHeader();return}
  Thread{
   fun getWeather(lat:String,lon:String,tz:String):Pair<String,String>?=try{
    val u="https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone="+java.net.URLEncoder.encode(tz,"UTF-8")
    val conn=URL(u).openConnection().apply{connectTimeout=5000;readTimeout=6000}
    val txt=conn.getInputStream().bufferedReader().use{it.readText()}
    val cur=JSONObject(txt).optJSONObject("current") ?: throw IllegalStateException("No current weather")
    val temp=Math.round(cur.optDouble("temperature_2m")).toInt().toString()
    temp to weatherName(cur.optInt("weather_code",-1))
   }catch(_:Exception){null}
   val a=getWeather("37.9838","23.7275","Europe/Athens")
   val s=getWeather("-33.8688","151.2093","Australia/Sydney")
   if(a!=null){athensTemp=a.first;athensCondition=a.second}
   if(s!=null){sydneyTemp=s.first;sydneyCondition=s.second}
   if(a!=null||s!=null)weatherLoadedAt=System.currentTimeMillis()
   runOnUiThread{if(screenMode=="HOME")updateHomeHeader()}
  }.start()
 }
 private fun shell(title:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(64,34,64,28);setBackgroundColor(bg);addView(TextView(this@MainActivity).apply{text=title;textSize=34f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.05f;setPadding(6,0,0,2)});addView(TextView(this@MainActivity).apply{text="Η Ελλάδα στο σπίτι σας  •  $placeUpper → WORLD";textSize=15f;setTextColor(accent);letterSpacing=.03f;setPadding(7,0,0,22)})}


 private fun showPreloadedMovies(){
  if(isPappas){showHome();return}
  screenMode="PRELOADED_MOVIES"
  previewHandler.removeCallbacksAndMessages(null)
  headerHandler.removeCallbacksAndMessages(null)
  previewPlayer?.release();previewPlayer=null
  player?.release();player=null
  activeHomeNav="Preloaded Movies"
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(52,30,58,34)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(6,24,40),Color.rgb(2,8,15)))
  }

  val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val titleWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleWrap.addView(TextView(this).apply{
   text="PRELOADED MOVIES";textSize=25f;letterSpacing=.025f
   typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);setSingleLine(true)
  })
  titleWrap.addView(TextView(this).apply{
   text="32 Greek-language films • classics to modern";textSize=10.5f
   setTextColor(Color.rgb(142,190,222));letterSpacing=.035f;setSingleLine(true)
  })
  head.addView(titleWrap,LinearLayout.LayoutParams(0,-2,1f))
  head.addView(button("←  Home"){showGreekOneHome()},LinearLayout.LayoutParams(150,52))
  root.addView(head,LinearLayout.LayoutParams(-1,68))

  val sourceNote=TextView(this).apply{
   text="CURATED GREEK CINEMA  •  32 TITLES"
   textSize=9.5f;letterSpacing=.09f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
   setTextColor(Color.rgb(97,196,244));setPadding(2,4,0,12)
  }
  root.addView(sourceNote)

  val scroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_NEVER}
  val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,0,18,20)}
  scroll.addView(content,ViewGroup.LayoutParams(-1,-2))

  val decadeRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  listOf("ALL","1950s","1960s","CLASSICS","MODERN").forEachIndexed{i,label->
   val chip=TextView(this).apply{
    text=label;textSize=10.5f;gravity=Gravity.CENTER;isFocusable=true;isClickable=true
    typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE)
    background=GradientDrawable().apply{
     setColor(if(i==0)Color.rgb(16,112,200) else Color.argb(155,5,27,46))
     cornerRadius=14f;setStroke(1,if(i==0)Color.argb(220,215,246,255) else Color.argb(65,120,183,225))
    }
    setOnFocusChangeListener{v,f->
     v.background=GradientDrawable().apply{
      setColor(if(f)Color.rgb(24,148,230) else if(i==0)Color.rgb(16,112,200) else Color.argb(155,5,27,46))
      cornerRadius=14f;setStroke(if(f)2 else 1,if(f)Color.WHITE else Color.argb(65,120,183,225))
     }
    }
   }
   decadeRow.addView(chip,LinearLayout.LayoutParams(104,36).apply{setMargins(0,0,10,0)})
  }
  content.addView(decadeRow,LinearLayout.LayoutParams(-1,46))

  data class MovieItem(val title:String,val year:String,val meta:String,val url:String,val accent:Int)
  val movies=listOf(
   MovieItem("Our Guardian Angel","1961","Comedy • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M000545",Color.rgb(37,91,126)),
   MovieItem("The Girl of the Neighborhood","1954","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M002260",Color.rgb(113,61,92)),
   MovieItem("Me, Myself and I","1964","Greek Cinema • Comedy","https://live.ertflix.gr/details/ERT_214813",Color.rgb(43,96,129)),
   MovieItem("Cry","1964","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M002490",Color.rgb(82,61,116)),

   MovieItem("The Mischief-Makers","Classic","Comedy • Old Greek Cinema","https://live.ertflix.gr/details/ERT_213212",Color.rgb(127,79,31)),
   MovieItem("The Big Shark","1957","Comedy • Romance • Old Greek Cinema","https://live.ertflix.gr/details/ERT_182067",Color.rgb(23,104,120)),
   MovieItem("Bouboulina","1959","Biography • Historical • Greek Cinema","https://live.ertflix.gr/details/ERT_P000052",Color.rgb(105,59,39)),
   MovieItem("The Refugee","1969","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M001256",Color.rgb(45,76,118)),

   MovieItem("Athens – Istanbul","2008","Drama • Adventure • Greek Cinema","https://live.ertflix.gr/details/ERT_M002494",Color.rgb(26,99,119)),
   MovieItem("Almond Tree in Bloom","Classic","Romance • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M000294",Color.rgb(118,70,52)),
   MovieItem("Blood Ties","2012","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M001448",Color.rgb(104,49,68)),
   MovieItem("The Poor Boy","Classic","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M001392",Color.rgb(59,76,112)),

   MovieItem("My Poor Little Sparrow","Classic","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M002279",Color.rgb(126,64,86)),
   MovieItem("The Hook","1976","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000567",Color.rgb(67,61,113)),
   MovieItem("Lefteris Dimakopoulos","1993","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000031",Color.rgb(39,82,117)),
   MovieItem("Exotic Vitamins","Classic","Comedy • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M002272",Color.rgb(25,105,95)),

   MovieItem("Liubi","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000372",Color.rgb(81,53,112)),
   MovieItem("Roza of Smyrna","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000440",Color.rgb(118,53,61)),
   MovieItem("The King","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000029",Color.rgb(56,75,112)),
   MovieItem("The Seventh Sun of Love","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000886",Color.rgb(111,60,85)),

   MovieItem("Drift","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000807",Color.rgb(38,87,118)),
   MovieItem("Love Under the Date Tree","Greek Cinema","Romance • Greek Cinema","https://live.ertflix.gr/details/ERT_M000338",Color.rgb(122,55,82)),
   MovieItem("Invincible Lovers","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P001777",Color.rgb(47,74,113)),
   MovieItem("Such a Long Absence","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000437",Color.rgb(73,60,112)),

   MovieItem("The Photographers","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M002473",Color.rgb(31,92,120)),
   MovieItem("Young Aphrodites","1963","Drama • Arthouse • Greek Cinema","https://live.ertflix.gr/details/ERT_M002468",Color.rgb(93,56,131)),
   MovieItem("Riviera","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000893",Color.rgb(20,102,125)),
   MovieItem("Rembetiko","Greek Cinema","Music • Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000496",Color.rgb(83,47,105)),

   MovieItem("Crows","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000345",Color.rgb(52,72,104)),
   MovieItem("The Tears of the Mountain","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M001148",Color.rgb(64,70,102)),
   MovieItem("Meteor and Shadow","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_271968",Color.rgb(49,78,108)),
   MovieItem("Coat Fitting","2006","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_214863",Color.rgb(91,56,88))
  )

  content.addView(TextView(this).apply{
   text="Greek Cinema";textSize=17f;typeface=Typeface.create("sans-serif",Typeface.BOLD)
   setTextColor(Color.WHITE);setPadding(0,14,0,8)
  })

  var movieRow:LinearLayout?=null
  movies.forEachIndexed{i,m->
   if(i%4==0){
    movieRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    content.addView(movieRow,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,12)})
   }
   val card=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
    setPadding(0,0,12,0)
    background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(6,24,39),Color.rgb(8,34,53))).apply{
     cornerRadius=15f;setStroke(1,Color.argb(72,150,200,232))
    }
    elevation=6f;setOnClickListener{openUri(m.url)}
   }
   val poster=FrameLayout(this).apply{
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(m.accent,Color.rgb(7,20,33))).apply{
     cornerRadii=floatArrayOf(15f,15f,0f,0f,0f,0f,15f,15f)
    }
   }
   val posterImage=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP}
   poster.addView(posterImage,FrameLayout.LayoutParams(-1,-1))
   poster.addView(View(this).apply{
    background=GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(Color.argb(205,2,9,16),Color.TRANSPARENT))
   },FrameLayout.LayoutParams(-1,-1))
   poster.addView(TextView(this@MainActivity).apply{
    text=m.year;textSize=8.5f;letterSpacing=.08f;typeface=Typeface.DEFAULT_BOLD
    setTextColor(Color.WHITE);setPadding(9,0,0,8);gravity=Gravity.BOTTOM
   },FrameLayout.LayoutParams(-1,-1))
   card.addView(poster,LinearLayout.LayoutParams(82,-1).apply{setMargins(0,0,12,0)})
   val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL}
   copy.addView(TextView(this@MainActivity).apply{
    text=m.title;textSize=13.5f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
    setTextColor(Color.WHITE);setMaxLines(2);ellipsize=android.text.TextUtils.TruncateAt.END
   })
   copy.addView(TextView(this@MainActivity).apply{
    text=m.meta;textSize=8.2f;setTextColor(Color.rgb(167,197,217));setMaxLines(2);setPadding(0,4,0,5)
   })
   copy.addView(TextView(this@MainActivity).apply{
    text=if(m.url.contains("ertflix.gr"))"ERTFLIX  ›" else "WATCH  ▶"
    textSize=8.5f;letterSpacing=.04f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(83,194,248))
   })
   card.addView(copy,LinearLayout.LayoutParams(0,-1,1f))
   val fallbackArt=listOf(
    "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=500&q=75",
    "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=500&q=75",
    "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?auto=format&fit=crop&w=500&q=75",
    "https://images.unsplash.com/photo-1478720568477-152d9b164e26?auto=format&fit=crop&w=500&q=75"
   )[i%4]
   loadImageInto(posterImage,fallbackArt)
   card.setOnFocusChangeListener{v,f->
    v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=15f;setStroke(3,Color.WHITE)}else null
    v.animate().scaleX(if(f)1.022f else 1f).scaleY(if(f)1.022f else 1f).setDuration(125).start()
    v.elevation=if(f)18f else 6f
   }
   movieRow?.addView(card,LinearLayout.LayoutParams(0,142,1f).apply{setMargins(0,0,12,0)})
  }

  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  content.post{
   for(i in 0 until content.childCount){
    val group=content.getChildAt(i)
    if(group is LinearLayout){
     for(j in 0 until group.childCount){
      val child=group.getChildAt(j)
      if(child.isFocusable){child.requestFocus();return@post}
     }
    }
   }
  }
 }

 private fun showGreekOneHome(){
  screenMode="HOME"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null);player?.release();player=null;previewPlayer?.release();previewPlayer=null
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

  val root=FrameLayout(this).apply{setBackgroundColor(bg)}
  val backdrop=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;alpha=.82f;setBackgroundColor(Color.rgb(2,8,15))}
  root.addView(backdrop,FrameLayout.LayoutParams(-1,-1))
  val hero=cfgString("heroUrl",if(isPappas)"https://commons.wikimedia.org/wiki/Special:Redirect/file/Nafplio_from_Palamidi_castle.jpg" else "https://commons.wikimedia.org/wiki/Special:Redirect/file/Sunset_at_%C3%87e%C5%9Fme_overlooking_Chios.jpg")
  imageCache.get(hero)?.let{backdrop.setImageBitmap(it)}?:Thread{try{
   val bmp=URL(hero).openStream().use{BitmapFactory.decodeStream(it)}
   if(bmp!=null)imageCache.put(hero,bmp)
   runOnUiThread{if(bmp!=null)backdrop.setImageBitmap(bmp)}
  }catch(_:Exception){}}.start()
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.argb(28,1,6,12),Color.argb(132,1,8,15),Color.argb(238,1,7,13)))},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,intArrayOf(Color.argb(58,36,143,214),Color.argb(16,36,143,214),Color.TRANSPARENT))},FrameLayout.LayoutParams(-1,200))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(120,0,8,18),Color.TRANSPARENT))},FrameLayout.LayoutParams(430,-1))

  val lowerVeil=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(95,1,8,15),Color.argb(185,1,7,13)))}
  root.addView(lowerVeil,FrameLayout.LayoutParams(-1,-1).apply{topMargin=150})
  val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(42,22,58,26);clipToPadding=false}

  // TV-safe masthead: compact brand, generous safe margins, no clipped right edge.
  val top=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(4,0,4,0)
  }
  if(isPappas){
   val identity=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
   identity.addView(TextView(this).apply{
    text="🇬🇷";textSize=38f;gravity=Gravity.CENTER
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(12,86,190),Color.rgb(8,55,132))).apply{cornerRadius=10f;setStroke(1,Color.argb(120,255,255,255))}
   },LinearLayout.LayoutParams(76,64).apply{setMargins(0,0,12,0)})
   identity.addView(TextView(this).apply{
    text=brandName;textSize=34f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);setSingleLine(true)
   })
   top.addView(identity,LinearLayout.LayoutParams(0,-2,1f))
   top.addView(TextView(this).apply{
    text=SimpleDateFormat("HH:mm   |   EEE d MMM",Locale.getDefault()).format(Date());textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.END;setSingleLine(true)
   })
  }else{
   val brand=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
   brand.addView(ImageView(this).apply{
    setImageResource(R.drawable.greek_one_mark);scaleType=ImageView.ScaleType.CENTER_INSIDE
   },LinearLayout.LayoutParams(58,58).apply{setMargins(0,0,12,0)})
   val bt=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   bt.addView(TextView(this@MainActivity).apply{
    text="GREEK ONE";textSize=29f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.02f;setSingleLine(true)
   })
   bt.addView(TextView(this@MainActivity).apply{
    text="GREEK TELEVISION";textSize=8.5f;letterSpacing=.18f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.rgb(145,195,229));setSingleLine(true)
   })
   brand.addView(bt,LinearLayout.LayoutParams(210,-2))
   top.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
   fun infoChip():TextView=TextView(this).apply{
    textSize=11.5f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER
    setPadding(12,7,12,7)
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(190,4,22,39),Color.argb(170,8,48,79))).apply{cornerRadius=15f;setStroke(1,Color.argb(80,144,203,242))}
   }
   athensInfoView=infoChip()
   dateInfoView=TextView(this).apply{
    textSize=10.5f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.rgb(222,235,245));gravity=Gravity.CENTER;letterSpacing=.05f;setSingleLine(true)
   }
   sydneyInfoView=infoChip()
   top.addView(athensInfoView,LinearLayout.LayoutParams(166,60).apply{setMargins(8,0,6,0)})
   top.addView(dateInfoView,LinearLayout.LayoutParams(92,60))
   top.addView(sydneyInfoView,LinearLayout.LayoutParams(170,60).apply{setMargins(6,0,6,0)})
   val settingsChip=TextView(this).apply{
    text="⚙";textSize=20f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;isFocusable=true;isClickable=true
    background=GradientDrawable().apply{setColor(Color.argb(165,5,24,42));cornerRadius=14f;setStroke(1,Color.argb(75,150,205,245))}
    setOnClickListener{activeHomeNav="Settings";showSettings()}
    setOnFocusChangeListener{v,f->v.background=GradientDrawable().apply{setColor(if(f)Color.rgb(12,120,210) else Color.argb(165,5,24,42));cornerRadius=14f;setStroke(if(f)2 else 1,if(f)Color.WHITE else Color.argb(75,150,205,245))}}
   }
   top.addView(settingsChip,LinearLayout.LayoutParams(50,50))
   updateHomeHeader();refreshHomeWeather();headerHandler.post(headerTick)
  }
  page.addView(top,LinearLayout.LayoutParams(-1,80))

  val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}

  // Premium navigation rail: focus is bright; current section is only a subtle marker.
  val nav=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(if(isPappas)10 else 14,if(isPappas)12 else 14,if(isPappas)10 else 14,12)
   background=if(isPappas)GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(242,1,11,22),Color.argb(226,3,22,39))).apply{cornerRadius=16f;setStroke(1,Color.argb(72,150,195,230))}
   else GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(248,2,13,26),Color.argb(240,3,27,47))).apply{
    cornerRadius=24f;setStroke(1,Color.argb(88,103,181,231))
   }
   elevation=if(isPappas)8f else 16f
  }
  if(!isPappas){
   val brandWrap=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(6,0,4,8)}
   brandWrap.addView(ImageView(this).apply{
    setImageResource(R.drawable.greek_one_mark);scaleType=ImageView.ScaleType.CENTER_INSIDE
   },LinearLayout.LayoutParams(42,42).apply{setMargins(0,0,10,0)})
   val brandText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL}
   brandText.addView(TextView(this).apply{
    text="GREEK ONE";textSize=15f;letterSpacing=.035f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE);setSingleLine(true)
   })
   brandText.addView(TextView(this).apply{
    text="TELEVISION";textSize=7.5f;letterSpacing=.16f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.rgb(108,171,211));setSingleLine(true)
   })
   brandWrap.addView(brandText,LinearLayout.LayoutParams(0,42,1f))
   nav.addView(brandWrap,LinearLayout.LayoutParams(-1,54))
  }

  val navItems=mutableListOf<Triple<String,String,()->Unit>>()
  fun addNav(icon:String,label:String,action:()->Unit){navItems.add(Triple(icon,label,{activeHomeNav=label;action()}))}
  addNav("⌂","Home"){showGreekOneHome()}
  addNav("▣","Live TV"){loadChannels()}
  addNav("♡","Favourites"){loadChannels(favouritesOnly=true)}
  addNav("◷","Continue"){loadLastChannel()}\n  addNav("◆","Preloaded Movies"){showPreloadedMovies()}
  addNav("▤","On Demand"){loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")}
  if(isPappas)addNav("●",placeName){loadChannels(placeFilter)}
  addNav("◎","World TV"){loadChannels("ΔΙΕΘΝΗ")}
  addNav("☷","Categories"){loadChannels()}
  addNav("▦","TV Guide"){showTvGuide()}
  if(!isPappas)addNav("▶","YouTube"){showYouTubeSearch()}
  addNav("⌕","Search"){loadChannels()}
  addNav("⚙","Settings"){showSettings()}

  navItems.forEachIndexed{i,item->
   val icon=item.first;val label=item.second;val action=item.third
   val selected=!isPappas&&label==activeHomeNav
   val row=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
    setPadding(8,0,10,0)
    layoutParams=LinearLayout.LayoutParams(-1,if(isPappas)50 else 43).apply{setMargins(0,1,0,1)}
    background=if(selected)GradientDrawable().apply{
     setColor(Color.argb(76,25,142,220));cornerRadius=13f
    }else GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=13f}
    setOnClickListener{action()}
   }
   val marker=View(this).apply{
    background=GradientDrawable().apply{setColor(if(selected)Color.rgb(55,196,255) else Color.TRANSPARENT);cornerRadius=3f}
   }
   row.addView(marker,LinearLayout.LayoutParams(4,24).apply{setMargins(0,0,9,0)})
   val iconView=TextView(this).apply{
    text=icon;textSize=15f;gravity=Gravity.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
    setTextColor(if(selected)Color.WHITE else Color.rgb(150,193,222))
   }
   row.addView(iconView,LinearLayout.LayoutParams(28,32).apply{setMargins(0,0,8,0)})
   val labelView=TextView(this).apply{
    text=label;textSize=13.4f;typeface=Typeface.create("sans-serif-medium",if(selected)Typeface.BOLD else Typeface.NORMAL)
    setTextColor(if(selected)Color.WHITE else Color.rgb(210,226,237));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END
   }
   row.addView(labelView,LinearLayout.LayoutParams(0,-1,1f))
   row.setOnFocusChangeListener{v,f->
    row.background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,104,198),Color.rgb(31,177,246))).apply{
     cornerRadius=13f;setStroke(2,Color.argb(245,232,250,255))
    }else if(selected)GradientDrawable().apply{setColor(Color.argb(76,25,142,220));cornerRadius=13f}
     else GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=13f}
    marker.background=GradientDrawable().apply{setColor(if(f||selected)Color.rgb(62,205,255) else Color.TRANSPARENT);cornerRadius=3f}
    iconView.setTextColor(if(f||selected)Color.WHITE else Color.rgb(150,193,222))
    labelView.setTextColor(if(f||selected)Color.WHITE else Color.rgb(210,226,237))
    labelView.typeface=Typeface.create("sans-serif-medium",if(f||selected)Typeface.BOLD else Typeface.NORMAL)
    v.animate().translationX(if(f)3f else 0f).scaleX(if(f)1.012f else 1f).scaleY(if(f)1.018f else 1f).setDuration(130).start()
    v.elevation=if(f)12f else 0f
   }
   nav.addView(row)
   if(!isPappas&&(i==3||i==8)){
    nav.addView(View(this).apply{setBackgroundColor(Color.argb(28,111,179,226))},LinearLayout.LayoutParams(-1,1).apply{setMargins(16,4,16,4)})
   }
  }

  body.addView(nav,LinearLayout.LayoutParams(if(isPappas)224 else 278,-1).apply{
   setMargins(0,6,if(isPappas)18 else 22,0)
  })

  val mainScroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_NEVER}
  val main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(2,0,18,26);clipToPadding=false}
  mainScroll.addView(main,ViewGroup.LayoutParams(-1,-2))
  fun sectionTitle(t:String){
   main.addView(TextView(this).apply{text=t;textSize=21f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE);setPadding(0,9,0,6);setShadowLayer(6f,0f,2f,Color.argb(120,0,0,0))})
  }
  if(!isPappas){
   val featured=homeCachedChannels().firstOrNull{it.name.contains("ERT 1",true)||it.name.contains("ERT1",true)}?:homeCachedChannels().firstOrNull()
   if(featured!=null){
    val heroCard=LinearLayout(this).apply{
     orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true;setPadding(20,14,20,14)
     background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(245,4,31,55),Color.argb(230,8,82,129),Color.argb(205,3,26,46))).apply{cornerRadius=20f;setStroke(1,Color.argb(120,130,207,250))}
     elevation=10f
    }
    val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(8,8,8,8);background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=14f}}
    heroCard.addView(logo,LinearLayout.LayoutParams(92,72).apply{setMargins(0,0,20,0)})
    loadImageInto(logo,channelLogoUrl(featured))
    val heroText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    heroText.addView(TextView(this@MainActivity).apply{this.text="FEATURED NOW";textSize=10f;letterSpacing=.14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(98,206,255))})
    heroText.addView(TextView(this@MainActivity).apply{this.text=featured.name;textSize=22f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)})
    heroText.addView(TextView(this@MainActivity).apply{this.text=epgNow[featured.tvgId]?.let{"NOW  •  "+it}?:"LIVE  •  Greek television";textSize=14f;setTextColor(Color.rgb(225,237,246));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END})
    heroText.addView(TextView(this@MainActivity).apply{this.text=epgNext[featured.tvgId]?.let{"NEXT •  "+it}?:"Press OK to watch";textSize=12f;setTextColor(Color.rgb(154,199,228));setPadding(0,3,0,0)})
    heroCard.addView(heroText,LinearLayout.LayoutParams(0,-2,1f))
    val watchButton=TextView(this).apply{
     setText("WATCH  ▶");textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER
     background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,115,216),Color.rgb(34,184,252))).apply{cornerRadius=14f}
    }
    heroCard.addView(watchButton,LinearLayout.LayoutParams(132,48))
    heroCard.setOnClickListener{playRecent(featured.url)}
    heroCard.setOnFocusChangeListener{v,f->v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(3,Color.WHITE);cornerRadius=20f}else null;v.animate().scaleX(if(f)1.018f else 1f).scaleY(if(f)1.018f else 1f).setDuration(145).start();v.elevation=if(f)20f else 10f}
    main.addView(heroCard,LinearLayout.LayoutParams(-1,116).apply{setMargins(0,4,0,8)})
   }
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
   val ch=homeChannel(a[1])
   val now=ch?.tvgId?.let{epgNow[it]}
   val next=ch?.tvgId?.let{epgNext[it]}
   val sub=when{now!=null&&next!=null->"NOW  •  $now\nNEXT •  $next";now!=null->"NOW  •  $now";else->a[1]}
   val action={if(ch!=null)playRecent(ch.url) else if(i<2)loadChannels("ERT") else loadChannels()}
   channelRow.addView(
    logoCard(a[0],sub,a[2].toInt(),a[3]=="1",action),
    LinearLayout.LayoutParams(0,120,1f).apply{setMargins(0,0,12,0)}
   )
  }
  main.addView(channelRow)

  sectionTitle("Recently Watched")
  val cont=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  run{
   val recent=try{JSONArray(prefs.getString("recent_channels","[]")?:"[]")}catch(_:Exception){JSONArray()}
   if(recent.length()==0){
    val cardView=imageCard("Start watching","Recently watched channels will appear here","https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"){loadChannels()}
    cont.addView(cardView,LinearLayout.LayoutParams(0,154,1f).apply{setMargins(0,0,12,0)})
   }else{
    for(i in 0 until minOf(4,recent.length())){
     val o=recent.optJSONObject(i)?:continue
     val name=o.optString("name","Channel")
     val group=o.optString("group","Recently watched")
     val url=o.optString("url","")
     val id=o.optString("tvgId","")
     val watchedAt=o.optLong("watchedAt",0L)
     val now=id.takeIf{it.isNotBlank()}?.let{epgNow[it]}
     val subtitle=if(now!=null)"NOW  •  $now\nRESUME • ${watchedAgo(watchedAt).removePrefix("Watched ")}" else "RESUME • ${watchedAgo(watchedAt)}"
     val image=when{
      group.contains("ERT",true)->"https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"
      group.contains("ΤΑΙΝ",true)->"https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1200&q=85"
      group.contains("ΜΟΥΣ",true)->"https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=85"
      else->"https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=85"
     }
     cont.addView(imageCard(name,subtitle,image){playRecent(url)},LinearLayout.LayoutParams(0,182,1f).apply{setMargins(0,0,12,0)})
    }
   }
  }
  main.addView(cont)

  sectionTitle("Browse by Category")
  val cats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val categoryData=mutableListOf<Array<String>>(
   arrayOf("▣  Greek TV","All Greek Channels",Color.rgb(18,124,210).toString()),
   arrayOf("◉  Movies","Greek & International",Color.rgb(155,26,83).toString()),
   arrayOf("▦  TV Guide","Now & Next",Color.rgb(4,116,68).toString()),
   arrayOf("★  Kids","For the Little Ones",Color.rgb(225,124,5).toString()),
   arrayOf("◎  World TV","International Channels",Color.rgb(95,19,160).toString())
  )
  if(isPappas)categoryData.add(4,arrayOf("◉  $placeName","Local Content",Color.rgb(6,132,153).toString()))
  categoryData.forEach{a->
   val action:()->Unit=when{
    a[0].contains("Greek TV")->{ {loadChannels()} }
    a[0].contains("Movies")->{ {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")} }
    a[0].contains("TV Guide")->{ {showTvGuide()} }
    a[0].contains("Kids")->{ {loadChannels("ΠΑΙΔΙΚΑ")} }
    a[0].contains(placeName)->{ {loadChannels(placeFilter)} }
    else->{ {loadChannels("ΔΙΕΘΝΗ")} }
   }
   cats.addView(tvCard(a[0],a[1],a[2].toInt(),action).apply{gravity=Gravity.CENTER_VERTICAL;elevation=4f},LinearLayout.LayoutParams(0,86,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(cats)

  sectionTitle("Live Channels")
  val liveRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val cachedHomeChannels=try{
   val cached=prefs.getString("playlist_cache",null)
   if(cached.isNullOrBlank()) emptyList() else parsePlaylist(cached)
  }catch(_:Exception){emptyList()}
  val preferredNames=listOf("ERT 1","ERT1","ERT 2","ANT1","ALPHA","SKAI","ΣΚΑΪ","OPEN","MEGA")
  val selectedHomeChannels=mutableListOf<Channel>()
  preferredNames.forEach{name->
   val found=cachedHomeChannels.firstOrNull{it.name.contains(name,true)&&selectedHomeChannels.none{x->x.url==it.url}}
   if(found!=null&&selectedHomeChannels.size<5)selectedHomeChannels.add(found)
  }
  if(selectedHomeChannels.size<5){
   cachedHomeChannels.forEach{ch->
    if(selectedHomeChannels.size<5&&selectedHomeChannels.none{x->x.url==ch.url})selectedHomeChannels.add(ch)
   }
  }
  if(selectedHomeChannels.isEmpty()){
   listOf("ERT 1","ANT1","ALPHA","SKAI","MEGA").forEach{name->
    liveRow.addView(tvCard("●  $name","Live TV",Color.rgb(16,74,132)){loadChannels()},LinearLayout.LayoutParams(0,100,1f).apply{setMargins(0,0,12,0)})
   }
  }else{
   selectedHomeChannels.forEach{ch->
    liveRow.addView(logoCard(ch.name,ch.name,Color.rgb(16,74,132),false,{playRecent(ch.url)},channelLogoUrl(ch)),LinearLayout.LayoutParams(0,100,1f).apply{setMargins(0,0,12,0)})
   }
  }
  main.addView(liveRow)

  body.addView(mainScroll,LinearLayout.LayoutParams(0,-1,1f))
  page.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(page)
  setContentView(root)
  nav.post{if(nav.childCount>0)nav.getChildAt(0).requestFocus()}
  val heroUrls=listOf(hero,
   "https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=1600&q=82",
   "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1600&q=82")
  var heroIndex=0
  lateinit var rotateHero:Runnable
  rotateHero=Runnable{
   if(screenMode!="HOME")return@Runnable
   heroIndex=(heroIndex+1)%heroUrls.size
   val u=heroUrls[heroIndex]
   fun applyBmp(bmp:Bitmap){backdrop.animate().alpha(.35f).setDuration(180).withEndAction{backdrop.setImageBitmap(bmp);backdrop.animate().alpha(.82f).setDuration(280).start()}.start()}
   imageCache.get(u)?.let{applyBmp(it)}?:Thread{try{val bmp=URL(u).openStream().use{BitmapFactory.decodeStream(it)};if(bmp!=null){imageCache.put(u,bmp);runOnUiThread{if(screenMode=="HOME")applyBmp(bmp)}}}catch(_:Exception){}}.start()
   previewHandler.postDelayed(rotateHero,18000)
  }
  previewHandler.postDelayed(rotateHero,18000)
  if(epgNow.isEmpty()&&!epgLoading){
   val cached=homeCachedChannels()
   if(cached.isNotEmpty()){channels=cached;loadEpg{if(screenMode=="HOME"&&epgNow.isNotEmpty())showHome()}}
  }
 }
 private fun showHome(){
  if(!isPappas){showGreekOneHome();return}
  screenMode="HOME"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null);player?.release();player=null;previewPlayer?.release();previewPlayer=null
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

  val root=FrameLayout(this).apply{setBackgroundColor(bg)}
  val backdrop=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;alpha=.82f;setBackgroundColor(Color.rgb(2,8,15))}
  root.addView(backdrop,FrameLayout.LayoutParams(-1,-1))
  val hero=cfgString("heroUrl",if(isPappas)"https://commons.wikimedia.org/wiki/Special:Redirect/file/Nafplio_from_Palamidi_castle.jpg" else "https://commons.wikimedia.org/wiki/Special:Redirect/file/Sunset_at_%C3%87e%C5%9Fme_overlooking_Chios.jpg")
  imageCache.get(hero)?.let{backdrop.setImageBitmap(it)}?:Thread{try{
   val bmp=URL(hero).openStream().use{BitmapFactory.decodeStream(it)}
   if(bmp!=null)imageCache.put(hero,bmp)
   runOnUiThread{if(bmp!=null)backdrop.setImageBitmap(bmp)}
  }catch(_:Exception){}}.start()
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.argb(28,1,6,12),Color.argb(132,1,8,15),Color.argb(238,1,7,13)))},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,intArrayOf(Color.argb(58,36,143,214),Color.argb(16,36,143,214),Color.TRANSPARENT))},FrameLayout.LayoutParams(-1,200))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(120,0,8,18),Color.TRANSPARENT))},FrameLayout.LayoutParams(430,-1))

  val lowerVeil=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(95,1,8,15),Color.argb(185,1,7,13)))}
  root.addView(lowerVeil,FrameLayout.LayoutParams(-1,-1).apply{topMargin=150})
  val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(34,20,46,24);clipToPadding=false}

  // Compact TV-safe masthead. Everything is weighted so nothing can run off-screen.
  val top=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(0,0,0,0)
  }
  if(isPappas){
   val identity=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
   identity.addView(TextView(this).apply{
    text="🇬🇷";textSize=34f;gravity=Gravity.CENTER
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(12,86,190),Color.rgb(8,55,132))).apply{cornerRadius=10f;setStroke(1,Color.argb(120,255,255,255))}
   },LinearLayout.LayoutParams(64,54).apply{setMargins(0,0,10,0)})
   identity.addView(TextView(this).apply{
    text=brandName;textSize=28f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);setSingleLine(true)
   })
   top.addView(identity,LinearLayout.LayoutParams(0,-2,1f))
   top.addView(TextView(this).apply{
    text=SimpleDateFormat("HH:mm   |   EEE d MMM",Locale.getDefault()).format(Date());textSize=13f;setTextColor(Color.WHITE);gravity=Gravity.END;setSingleLine(true)
   },LinearLayout.LayoutParams(0,-2,.55f))
  }else{
   val left=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
   left.addView(ImageView(this).apply{
    setImageResource(R.drawable.greek_one_mark)
    scaleType=ImageView.ScaleType.CENTER_INSIDE
   },LinearLayout.LayoutParams(44,44).apply{setMargins(0,0,10,0)})
   val bt=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL}
   bt.addView(TextView(this@MainActivity).apply{
    text="GREEK ONE";textSize=21f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.015f;setSingleLine(true)
   })
   bt.addView(TextView(this@MainActivity).apply{
    text="GREEK TELEVISION";textSize=7f;letterSpacing=.14f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.rgb(142,190,222));setSingleLine(true)
   })
   left.addView(bt,LinearLayout.LayoutParams(0,-2,1f))
   top.addView(left,LinearLayout.LayoutParams(0,54,.72f))

   val centre=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
   fun miniInfo():TextView=TextView(this).apply{
    textSize=9.5f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER
    setPadding(8,5,8,5);setSingleLine(false)
    background=GradientDrawable().apply{setColor(Color.argb(145,4,24,42));cornerRadius=12f;setStroke(1,Color.argb(55,145,205,242))}
   }
   athensInfoView=miniInfo()
   dateInfoView=TextView(this).apply{
    textSize=9f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.rgb(218,232,243));gravity=Gravity.CENTER;setSingleLine(true)
   }
   sydneyInfoView=miniInfo()
   centre.addView(athensInfoView,LinearLayout.LayoutParams(0,50,1f).apply{setMargins(4,0,4,0)})
   centre.addView(dateInfoView,LinearLayout.LayoutParams(0,50,.56f))
   centre.addView(sydneyInfoView,LinearLayout.LayoutParams(0,50,1f).apply{setMargins(4,0,4,0)})
   top.addView(centre,LinearLayout.LayoutParams(0,54,1.25f))

   val settingsChip=TextView(this).apply{
    text="⚙";textSize=18f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;isFocusable=true;isClickable=true
    background=GradientDrawable().apply{setColor(Color.argb(145,5,24,42));cornerRadius=12f;setStroke(1,Color.argb(55,150,205,245))}
    setOnClickListener{activeHomeNav="Settings";showSettings()}
    setOnFocusChangeListener{v,f->v.background=GradientDrawable().apply{setColor(if(f)Color.rgb(12,120,210) else Color.argb(145,5,24,42));cornerRadius=12f;setStroke(if(f)2 else 1,if(f)Color.WHITE else Color.argb(55,150,205,245))}}
   }
   top.addView(settingsChip,LinearLayout.LayoutParams(42,42).apply{setMargins(8,0,0,0)})
   updateHomeHeader();refreshHomeWeather();headerHandler.post(headerTick)
  }
  page.addView(top,LinearLayout.LayoutParams(-1,64))

  val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}

  // Clean TV navigation rail. Current section is subtle; remote focus is the only bright blue state.
  val nav=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(if(isPappas)10 else 12,if(isPappas)12 else 10,if(isPappas)10 else 12,10)
   background=if(isPappas)GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(242,1,11,22),Color.argb(226,3,22,39))).apply{cornerRadius=16f;setStroke(1,Color.argb(72,150,195,230))}
   else GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(247,1,12,25),Color.argb(238,3,28,47))).apply{
    cornerRadius=22f;setStroke(1,Color.argb(70,98,173,223))
   }
   elevation=if(isPappas)8f else 14f
  }

  if(!isPappas){
   val railMark=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(5,0,5,6)}
   railMark.addView(ImageView(this).apply{
    setImageResource(R.drawable.greek_one_mark);scaleType=ImageView.ScaleType.CENTER_INSIDE
   },LinearLayout.LayoutParams(34,34).apply{setMargins(0,0,8,0)})
   railMark.addView(TextView(this).apply{
    text="GREEK ONE";textSize=13f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE);setSingleLine(true);letterSpacing=.025f
   },LinearLayout.LayoutParams(0,34,1f))
   nav.addView(railMark,LinearLayout.LayoutParams(-1,42))
  }

  val navItems=mutableListOf<Triple<String,String,()->Unit>>()
  fun addNav(icon:String,label:String,action:()->Unit){navItems.add(Triple(icon,label,{activeHomeNav=label;action()}))}
  addNav("⌂","Home"){showHome()}
  addNav("▣","Live TV"){loadChannels()}
  addNav("♡","Favourites"){loadChannels(favouritesOnly=true)}
  addNav("◷","Continue"){loadLastChannel()}
  addNav("▤","On Demand"){loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")}
  if(isPappas)addNav("●",placeName){loadChannels(placeFilter)}
  addNav("◎","World TV"){loadChannels("ΔΙΕΘΝΗ")}
  addNav("☷","Categories"){loadChannels()}
  addNav("▦","TV Guide"){showTvGuide()}
  if(!isPappas)addNav("▶","YouTube"){showYouTubeSearch()}
  addNav("⌕","Search"){loadChannels()}
  addNav("⚙","Settings"){showSettings()}

  navItems.forEachIndexed{i,item->
   val icon=item.first;val label=item.second;val action=item.third
   val selected=!isPappas&&label==activeHomeNav
   val row=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
    setPadding(7,0,9,0)
    layoutParams=LinearLayout.LayoutParams(-1,if(isPappas)50 else 39).apply{setMargins(0,1,0,1)}
    background=GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=12f}
    setOnClickListener{action()}
   }
   val marker=View(this).apply{background=GradientDrawable().apply{setColor(if(selected)Color.rgb(38,166,235) else Color.TRANSPARENT);cornerRadius=2f}}
   row.addView(marker,LinearLayout.LayoutParams(3,20).apply{setMargins(0,0,8,0)})
   row.addView(TextView(this).apply{
    text=icon;textSize=13f;gravity=Gravity.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
    setTextColor(if(selected)Color.rgb(204,238,255) else Color.rgb(145,187,214))
   },LinearLayout.LayoutParams(26,28).apply{setMargins(0,0,7,0)})
   val labelView=TextView(this).apply{
    text=label;textSize=12.8f;typeface=Typeface.create("sans-serif-medium",if(selected)Typeface.BOLD else Typeface.NORMAL)
    setTextColor(if(selected)Color.WHITE else Color.rgb(207,222,233));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END
   }
   row.addView(labelView,LinearLayout.LayoutParams(0,-1,1f))
   row.setOnFocusChangeListener{v,f->
    row.background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(10,103,192),Color.rgb(31,165,232))).apply{
     cornerRadius=12f;setStroke(2,Color.argb(235,235,251,255))
    }else GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=12f}
    marker.background=GradientDrawable().apply{setColor(if(f||selected)Color.rgb(58,199,255) else Color.TRANSPARENT);cornerRadius=2f}
    labelView.setTextColor(if(f||selected)Color.WHITE else Color.rgb(207,222,233))
    labelView.typeface=Typeface.create("sans-serif-medium",if(f||selected)Typeface.BOLD else Typeface.NORMAL)
    v.animate().translationX(if(f)2f else 0f).scaleX(if(f)1.008f else 1f).scaleY(if(f)1.012f else 1f).setDuration(120).start()
    v.elevation=if(f)10f else 0f
   }
   nav.addView(row)
   if(!isPappas&&(i==3||i==7)){
    nav.addView(View(this).apply{setBackgroundColor(Color.argb(24,111,179,226))},LinearLayout.LayoutParams(-1,1).apply{setMargins(14,3,14,3)})
   }
  }

  body.addView(nav,LinearLayout.LayoutParams(if(isPappas)224 else 210,-1).apply{
   setMargins(0,4,if(isPappas)18 else 18,0)
  })

  val mainScroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_NEVER}
  val main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,0,26,30);clipToPadding=false}
  mainScroll.addView(main,ViewGroup.LayoutParams(-1,-2))
  fun sectionTitle(t:String){
   main.addView(TextView(this).apply{text=t;textSize=19.5f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE);setPadding(0,9,0,6);setShadowLayer(6f,0f,2f,Color.argb(120,0,0,0))})
  }
  if(!isPappas){
   val featured=homeCachedChannels().firstOrNull{it.name.contains("ERT 1",true)||it.name.contains("ERT1",true)}?:homeCachedChannels().firstOrNull()
   if(featured!=null){
    val heroCard=LinearLayout(this).apply{
     orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true;setPadding(20,14,20,14)
     background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(245,4,31,55),Color.argb(230,8,82,129),Color.argb(205,3,26,46))).apply{cornerRadius=20f;setStroke(1,Color.argb(120,130,207,250))}
     elevation=10f
    }
    val logo=ImageView(this).apply{
     scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(7,7,7,7)
     background=GradientDrawable().apply{setColor(Color.argb(210,7,31,52));cornerRadius=12f;setStroke(1,Color.argb(80,130,190,230))}
    }
    heroCard.addView(logo,LinearLayout.LayoutParams(74,60).apply{setMargins(0,0,20,0)})
    loadImageInto(logo,channelLogoUrl(featured))
    val heroText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    heroText.addView(TextView(this@MainActivity).apply{this.text="FEATURED NOW";textSize=10f;letterSpacing=.14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(98,206,255))})
    heroText.addView(TextView(this@MainActivity).apply{this.text=featured.name;textSize=19f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)})
    heroText.addView(TextView(this@MainActivity).apply{this.text=epgNow[featured.tvgId]?.let{"NOW  •  "+it}?:"LIVE  •  Greek television";textSize=14f;setTextColor(Color.rgb(225,237,246));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END})
    heroText.addView(TextView(this@MainActivity).apply{this.text=epgNext[featured.tvgId]?.let{"NEXT •  "+it}?:"Press OK to watch";textSize=12f;setTextColor(Color.rgb(154,199,228));setPadding(0,3,0,0)})
    heroCard.addView(heroText,LinearLayout.LayoutParams(0,-2,1f))
    val watchButton=TextView(this).apply{
     setText("WATCH  ▶");textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER
     background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,115,216),Color.rgb(34,184,252))).apply{cornerRadius=14f}
    }
    heroCard.addView(watchButton,LinearLayout.LayoutParams(112,44))
    heroCard.setOnClickListener{playRecent(featured.url)}
    heroCard.setOnFocusChangeListener{v,f->v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(3,Color.WHITE);cornerRadius=20f}else null;v.animate().scaleX(if(f)1.018f else 1f).scaleY(if(f)1.018f else 1f).setDuration(145).start();v.elevation=if(f)20f else 10f}
    main.addView(heroCard,LinearLayout.LayoutParams(-1,100).apply{setMargins(0,4,0,8)})
   }
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
   val ch=homeChannel(a[1])
   val now=ch?.tvgId?.let{epgNow[it]}
   val next=ch?.tvgId?.let{epgNext[it]}
   val sub=when{now!=null&&next!=null->"NOW  •  $now\nNEXT •  $next";now!=null->"NOW  •  $now";else->a[1]}
   val action={if(ch!=null)playRecent(ch.url) else if(i<2)loadChannels("ERT") else loadChannels()}
   channelRow.addView(
    logoCard(a[0],sub,a[2].toInt(),a[3]=="1",action),
    LinearLayout.LayoutParams(0,108,1f).apply{setMargins(0,0,12,0)}
   )
  }
  main.addView(channelRow)

  sectionTitle("Recently Watched")
  val cont=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  run{
   val recent=try{JSONArray(prefs.getString("recent_channels","[]")?:"[]")}catch(_:Exception){JSONArray()}
   if(recent.length()==0){
    val cardView=imageCard("Start watching","Recently watched channels will appear here","https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"){loadChannels()}
    cont.addView(cardView,LinearLayout.LayoutParams(0,140,1f).apply{setMargins(0,0,12,0)})
   }else{
    for(i in 0 until minOf(4,recent.length())){
     val o=recent.optJSONObject(i)?:continue
     val name=o.optString("name","Channel")
     val group=o.optString("group","Recently watched")
     val url=o.optString("url","")
     val id=o.optString("tvgId","")
     val watchedAt=o.optLong("watchedAt",0L)
     val now=id.takeIf{it.isNotBlank()}?.let{epgNow[it]}
     val subtitle=if(now!=null)"NOW  •  $now\nRESUME • ${watchedAgo(watchedAt).removePrefix("Watched ")}" else "RESUME • ${watchedAgo(watchedAt)}"
     val image=when{
      group.contains("ERT",true)->"https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"
      group.contains("ΤΑΙΝ",true)->"https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1200&q=85"
      group.contains("ΜΟΥΣ",true)->"https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=85"
      else->"https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=85"
     }
     cont.addView(imageCard(name,subtitle,image){playRecent(url)},LinearLayout.LayoutParams(0,182,1f).apply{setMargins(0,0,12,0)})
    }
   }
  }
  main.addView(cont)

  sectionTitle("Browse by Category")
  val cats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val categoryData=mutableListOf<Array<String>>(
   arrayOf("▣  Greek TV","All Greek Channels",Color.rgb(18,124,210).toString()),
   arrayOf("◉  Movies","Greek & International",Color.rgb(155,26,83).toString()),
   arrayOf("▦  TV Guide","Now & Next",Color.rgb(4,116,68).toString()),
   arrayOf("★  Kids","For the Little Ones",Color.rgb(225,124,5).toString()),
   arrayOf("◎  World TV","International Channels",Color.rgb(95,19,160).toString())
  )
  if(isPappas)categoryData.add(4,arrayOf("◉  $placeName","Local Content",Color.rgb(6,132,153).toString()))
  categoryData.forEach{a->
   val action:()->Unit=when{
    a[0].contains("Greek TV")->{ {loadChannels()} }
    a[0].contains("Movies")->{ {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")} }
    a[0].contains("TV Guide")->{ {showTvGuide()} }
    a[0].contains("Kids")->{ {loadChannels("ΠΑΙΔΙΚΑ")} }
    a[0].contains(placeName)->{ {loadChannels(placeFilter)} }
    else->{ {loadChannels("ΔΙΕΘΝΗ")} }
   }
   cats.addView(tvCard(a[0],a[1],a[2].toInt(),action).apply{gravity=Gravity.CENTER_VERTICAL;elevation=4f},LinearLayout.LayoutParams(0,92,1f).apply{setMargins(0,0,10,0)})
  }
  main.addView(cats)

  sectionTitle("Live Channels")
  val liveRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val cachedHomeChannels=try{
   val cached=prefs.getString("playlist_cache",null)
   if(cached.isNullOrBlank()) emptyList() else parsePlaylist(cached)
  }catch(_:Exception){emptyList()}
  val preferredNames=listOf("ERT 1","ERT1","ERT 2","ANT1","ALPHA","SKAI","ΣΚΑΪ","OPEN","MEGA")
  val selectedHomeChannels=mutableListOf<Channel>()
  preferredNames.forEach{name->
   val found=cachedHomeChannels.firstOrNull{it.name.contains(name,true)&&selectedHomeChannels.none{x->x.url==it.url}}
   if(found!=null&&selectedHomeChannels.size<4)selectedHomeChannels.add(found)
  }
  if(selectedHomeChannels.size<4){
   cachedHomeChannels.forEach{ch->
    if(selectedHomeChannels.size<4&&selectedHomeChannels.none{x->x.url==ch.url})selectedHomeChannels.add(ch)
   }
  }
  if(selectedHomeChannels.isEmpty()){
   listOf("ERT 1","ANT1","ALPHA","SKAI","MEGA").forEach{name->
    liveRow.addView(tvCard("●  $name","Live TV",Color.rgb(16,74,132)){loadChannels()},LinearLayout.LayoutParams(0,96,1f).apply{setMargins(0,0,10,0)})
   }
  }else{
   selectedHomeChannels.forEach{ch->
    liveRow.addView(logoCard(ch.name,ch.name,Color.rgb(16,74,132),false,{playRecent(ch.url)},channelLogoUrl(ch)),LinearLayout.LayoutParams(0,96,1f).apply{setMargins(0,0,10,0)})
   }
  }
  main.addView(liveRow)

  body.addView(mainScroll,LinearLayout.LayoutParams(0,-1,1f))
  page.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(page)
  setContentView(root)
  nav.post{if(nav.childCount>0)nav.getChildAt(0).requestFocus()}
  val heroUrls=listOf(hero,
   "https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=1600&q=82",
   "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1600&q=82")
  var heroIndex=0
  lateinit var rotateHero:Runnable
  rotateHero=Runnable{
   if(screenMode!="HOME")return@Runnable
   heroIndex=(heroIndex+1)%heroUrls.size
   val u=heroUrls[heroIndex]
   fun applyBmp(bmp:Bitmap){backdrop.animate().alpha(.35f).setDuration(180).withEndAction{backdrop.setImageBitmap(bmp);backdrop.animate().alpha(.82f).setDuration(280).start()}.start()}
   imageCache.get(u)?.let{applyBmp(it)}?:Thread{try{val bmp=URL(u).openStream().use{BitmapFactory.decodeStream(it)};if(bmp!=null){imageCache.put(u,bmp);runOnUiThread{if(screenMode=="HOME")applyBmp(bmp)}}}catch(_:Exception){}}.start()
   previewHandler.postDelayed(rotateHero,18000)
  }
  previewHandler.postDelayed(rotateHero,18000)
  if(epgNow.isEmpty()&&!epgLoading){
   val cached=homeCachedChannels()
   if(cached.isNotEmpty()){channels=cached;loadEpg{if(screenMode=="HOME"&&epgNow.isNotEmpty())showHome()}}
  }
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
 private fun logoCard(mark:String,label:String,base:Int,darkText:Boolean=false,action:()->Unit,logoUrlOverride:String=""):FrameLayout{
  val frame=FrameLayout(this).apply{
   isFocusable=true;isClickable=true;elevation=6f;clipToOutline=true
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,if(darkText)Color.rgb(222,226,232) else Color.rgb(5,18,33))).apply{cornerRadius=14f;setStroke(1,Color.argb(105,170,205,235))}
  }
  val logoUrl=logoUrlOverride.ifBlank{popularLogoUrl(label)}
  if(logoUrl.isNotBlank()){
   val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(12,8,12,8)}
   frame.addView(logo,FrameLayout.LayoutParams(-1,64))
   loadImageInto(logo,logoUrl)
  }else{
   val logo=TextView(this).apply{
    text=mark;textSize=23f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);gravity=Gravity.CENTER
    setTextColor(if(darkText)Color.rgb(23,50,130) else Color.WHITE);setShadowLayer(if(darkText)0f else 5f,0f,2f,Color.argb(120,0,0,0))
   }
   frame.addView(logo,FrameLayout.LayoutParams(-1,64))
  }
  val cap=TextView(this).apply{
   text=label;textSize=9.5f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
   background=GradientDrawable().apply{setColor(Color.argb(220,1,10,20));cornerRadii=floatArrayOf(0f,0f,0f,0f,14f,14f,14f,14f)}
   setPadding(4,3,4,4)
  }
  frame.addView(cap,FrameLayout.LayoutParams(-1,27,Gravity.BOTTOM))
  frame.setOnClickListener{action()}
  frame.setOnFocusChangeListener{v,f->
   v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(4,Color.WHITE);cornerRadius=14f}else null
   v.animate().scaleX(if(f)1.055f else 1f).scaleY(if(f)1.055f else 1f).translationZ(if(f)8f else 0f).setDuration(145).start();v.elevation=if(f)20f else 6f
  }
  return frame
 }
 private fun tvCard(title:String,subtitle:String="",base:Int=card,action:()->Unit):LinearLayout{
  return LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
   setPadding(14,8,14,8)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,Color.rgb(7,18,31))).apply{cornerRadius=14f;setStroke(1,Color.argb(90,150,195,230))}
   addView(TextView(this@MainActivity).apply{text=title;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END})
   if(subtitle.isNotBlank())addView(TextView(this@MainActivity).apply{text=subtitle;textSize=10f;setTextColor(Color.rgb(218,228,238));setPadding(0,2,0,0);setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END})
   setOnClickListener{action()}
   setOnFocusChangeListener{v,f->
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(if(f)focus else base,Color.rgb(6,19,34))).apply{cornerRadius=14f;setStroke(if(f)3 else 1,if(f)Color.WHITE else Color.argb(90,150,195,230))}
    v.animate().scaleX(if(f)1.055f else 1f).scaleY(if(f)1.055f else 1f).setDuration(145).start();v.elevation=if(f)18f else 2f
   }
  }
 }
 private fun imageCard(title:String,subtitle:String,url:String,action:()->Unit):FrameLayout{
  val frame=FrameLayout(this).apply{isFocusable=true;isClickable=true;background=panel(Color.rgb(8,20,34),16f);elevation=5f;clipToOutline=true}
  val img=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.rgb(16,30,44))}
  frame.addView(img,FrameLayout.LayoutParams(-1,-1))
  loadImageInto(img,url)
  val shade=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(Color.argb(242,2,8,14),Color.argb(118,2,8,14),Color.argb(24,2,8,14)))}
  frame.addView(shade,FrameLayout.LayoutParams(-1,-1))
  val textWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8)}
  textWrap.addView(TextView(this).apply{text=title;textSize=14.5f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);setShadowLayer(6f,0f,2f,Color.BLACK)})
  textWrap.addView(TextView(this).apply{text=subtitle;textSize=10.5f;setTextColor(Color.rgb(230,237,244))})
  frame.addView(textWrap,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  frame.setOnClickListener{action()}
  frame.setOnFocusChangeListener{v,f->v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(4,Color.WHITE);cornerRadius=14f}else null;v.animate().scaleX(if(f)1.05f else 1f).scaleY(if(f)1.05f else 1f).translationZ(if(f)7f else 0f).setDuration(145).start();v.elevation=if(f)18f else 3f}
  return frame
 }
 private fun showTvGuide(){
  screenMode="GUIDE"
  previewHandler.removeCallbacksAndMessages(null);player?.release();player=null;previewPlayer?.release();previewPlayer=null
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
 private fun openYouTubeExternal(url:String){
  val uri=Uri.parse(url)
  val packages=listOf("com.google.android.youtube.tv","com.google.android.youtube")
  for(pkg in packages){
   try{
    val intent=Intent(Intent.ACTION_VIEW,uri).apply{setPackage(pkg);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}
    if(intent.resolveActivity(packageManager)!=null){startActivity(intent);return}
   }catch(_:Exception){}
  }
  openUri(url)
 }
 private fun showYouTubeResults(query:String){
  val q=query.trim()
  if(q.isBlank())return
  screenMode="YOUTUBE_RESULTS"
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(5,25,44),Color.rgb(2,9,17)))
  }

  lateinit var web:WebView
  val bar=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
   setPadding(22,12,22,12)
   background=GradientDrawable().apply{setColor(Color.argb(248,3,17,30));setStroke(1,Color.argb(80,130,190,230))}
  }

  val input=EditText(this).apply{
   setText(q);setSelection(text.length)
   hint="Search YouTube…"
   setHintTextColor(Color.rgb(135,166,190));setTextColor(Color.WHITE);textSize=18f
   isSingleLine=true;inputType=android.text.InputType.TYPE_CLASS_TEXT
   setPadding(18,0,18,0)
   background=GradientDrawable().apply{setColor(Color.argb(210,3,23,40));cornerRadius=14f;setStroke(1,Color.argb(95,125,190,230))}
   isFocusable=true;isFocusableInTouchMode=true
   setOnFocusChangeListener{v,f->
    background=GradientDrawable().apply{
     setColor(if(f)Color.argb(235,5,38,63) else Color.argb(210,3,23,40))
     cornerRadius=14f
     setStroke(if(f)2 else 1,if(f)Color.rgb(89,201,255) else Color.argb(95,125,190,230))
    }
   }
  }

  fun runSearch(){
   val next=input.text.toString().trim()
   if(next.isBlank())return
   val url="https://www.youtube.com/results?search_query="+java.net.URLEncoder.encode(next,"UTF-8")
   try{
    web.loadUrl(url)
    input.clearFocus()
    web.requestFocus()
   }catch(_:Exception){openYouTubeExternal(url)}
  }
  input.setOnEditorActionListener{_,_,_->runSearch();true}
  input.setOnKeyListener{_,key,event->
   if(event.action==KeyEvent.ACTION_UP&&(key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_DPAD_CENTER)){runSearch();true}else false
  }
  input.setOnClickListener{
   input.requestFocus()
   (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)
  }

  bar.addView(TextView(this).apply{
   text="YouTube";textSize=19f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL
  },LinearLayout.LayoutParams(118,54).apply{setMargins(0,0,12,0)})
  bar.addView(input,LinearLayout.LayoutParams(0,56,1f).apply{setMargins(0,0,12,0)})
  bar.addView(button("Search ▶"){runSearch()},LinearLayout.LayoutParams(150,56).apply{setMargins(0,0,10,0)})
  bar.addView(button("← Back"){showYouTubeSearch()},LinearLayout.LayoutParams(132,56))
  root.addView(bar,LinearLayout.LayoutParams(-1,80))

  web=WebView(this).apply{
   setBackgroundColor(Color.rgb(2,8,15))
   isFocusable=true;isFocusableInTouchMode=true
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   settings.mediaPlaybackRequiresUserGesture=true
   settings.cacheMode=WebSettings.LOAD_DEFAULT
   webViewClient=object:WebViewClient(){
    override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{
     val u=request?.url?.toString()?:""
     if(u.contains("youtube.com/watch")||u.contains("youtu.be/")){openYouTubeExternal(u);return true}
     return false
    }
    override fun onReceivedError(view:WebView?,request:WebResourceRequest?,error:WebResourceError?){
     if(request?.isForMainFrame==true)Toast.makeText(this@MainActivity,"YouTube results could not load.",Toast.LENGTH_SHORT).show()
    }
   }
  }
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)

  val url="https://www.youtube.com/results?search_query="+java.net.URLEncoder.encode(q,"UTF-8")
  try{
   web.loadUrl(url)
   web.requestFocus()
  }catch(_:Exception){openYouTubeExternal(url)}
 }
 private fun openYouTubeSearch(query:String){showYouTubeResults(query)}
 private fun showYouTubeSearch(){
  if(isPappas){showHome();return}
  screenMode="YOUTUBE"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null);previewPlayer?.release();previewPlayer=null
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(54,34,54,30)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(5,25,44),Color.rgb(2,9,17)))
  }
  val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val titleWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleWrap.addView(TextView(this).apply{
   text="YouTube";textSize=34f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)
  })
  titleWrap.addView(TextView(this).apply{
   text="Search YouTube from Greek One";textSize=13f;setTextColor(Color.rgb(129,190,235));letterSpacing=.04f
  })
  head.addView(titleWrap,LinearLayout.LayoutParams(0,-2,1f))
  head.addView(button("←  Home"){showHome()},LinearLayout.LayoutParams(180,58))
  root.addView(head,LinearLayout.LayoutParams(-1,82))

  val searchPanel=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(18,14,18,14)
   background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(245,5,23,40),Color.argb(232,8,48,78))).apply{cornerRadius=20f;setStroke(1,Color.argb(95,145,205,245))}
   elevation=10f
  }
  val input=EditText(this).apply{
   hint="Search videos, channels or topics…"
   setHintTextColor(Color.rgb(145,172,195));setTextColor(Color.WHITE);textSize=20f
   isSingleLine=true
   inputType=android.text.InputType.TYPE_CLASS_TEXT
   background=GradientDrawable().apply{setColor(Color.argb(185,2,14,26));cornerRadius=15f;setStroke(1,Color.argb(75,125,185,225))}
   setPadding(20,0,20,0);isFocusable=true;isFocusableInTouchMode=true
   setOnEditorActionListener{_,_,_->if(text.toString().isNotBlank()){openYouTubeSearch(text.toString());true}else false}
  }
  searchPanel.addView(input,LinearLayout.LayoutParams(0,64,1f).apply{setMargins(0,0,14,0)})
  searchPanel.addView(button("Search ▶"){openYouTubeSearch(input.text.toString())},LinearLayout.LayoutParams(190,64))
  root.addView(searchPanel,LinearLayout.LayoutParams(-1,96))

  root.addView(TextView(this).apply{
   text="Popular searches";textSize=22f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);setPadding(2,24,0,12)
  })

  val quick1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val quick2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val quick=listOf(
   "Greek News" to "Greek news live",
   "Greek Music" to "Greek music",
   "Greek Comedy" to "Greek comedy",
   "Greek Cooking" to "Greek cooking recipes",
   "Documentaries" to "Greece documentary",
   "Greek Kids" to "Greek kids cartoons"
  )
  quick.forEachIndexed{i,(label,q)->
   val card=tvCard("▶  $label","Search YouTube",when(i){0->Color.rgb(163,25,38);1->Color.rgb(101,36,155);2->Color.rgb(16,117,185);3->Color.rgb(16,123,92);4->Color.rgb(77,91,111);else->Color.rgb(214,120,16)}){openYouTubeSearch(q)}
   val row=if(i<3)quick1 else quick2
   row.addView(card,LinearLayout.LayoutParams(0,104,1f).apply{setMargins(0,0,14,0)})
  }
  root.addView(quick1)
  root.addView(quick2,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,14,0,0)})

  root.addView(TextView(this).apply{
   text="Search stays inside Greek One until you choose a result. Playback opens in the official YouTube TV app when available."
   textSize=12f;setTextColor(Color.rgb(145,176,201));setPadding(4,22,0,0)
  })
  setContentView(root)
  input.requestFocus()
 } 
 private fun loadLastChannel(){val u=prefs.getString("last_channel",null);if(u==null){loadChannels();return};Thread{try{val all=parsePlaylist(fetchPlaylist());runOnUiThread{channels=all;val i=all.indexOfFirst{it.url==u};if(i>=0)play(i)else showList()}}catch(e:Exception){runOnUiThread{loadChannels()}}}.start()}
 private fun fetchPlaylist():String{
  val cached=prefs.getString("playlist_cache",null)
  val cachedAt=prefs.getLong("playlist_cache_at",0L)
  val now=System.currentTimeMillis()
  if(cached!=null&&now-cachedAt<5*60*1000L)return cached
  val u=URL("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u")
  val conn=u.openConnection().apply{connectTimeout=5000;readTimeout=8000}
  return conn.getInputStream().bufferedReader().use{it.readText()}.also{prefs.edit().putString("playlist_cache",it).putLong("playlist_cache_at",now).apply()}
 }
 private fun parsePlaylist(txt:String):List<Channel>{val out=mutableListOf<Channel>();var n="";var g="";var id="";txt.lines().forEach{l->if(l.startsWith("#EXTINF")){n=l.substringAfterLast(",").trim();g=l.substringAfter("group-title=\"", "").substringBefore("\"", "");id=l.substringAfter("tvg-id=\"", "").substringBefore("\"", "")}else if(l.startsWith("http")&&n.isNotBlank()){out.add(Channel(n,l.trim(),g,id));n="";g="";id=""}};return out}
 private fun loadChannels(filter:String?=null,favouritesOnly:Boolean=false){currentSection=when{favouritesOnly->"FAVOURITES";filter?.contains(placeFilter,true)==true->placeUpper;filter?.contains("ΠΑΙΔΙΚΑ",true)==true->"KIDS";filter?.contains("ΤΑΙΝΙΕΣ",true)==true->"ON DEMAND";filter?.contains("ΔΙΕΘΝΗ",true)==true->"WORLD TV";filter?.contains("ERT",true)==true->"ERT";else->"LIVE TV"};showLoadingState("Loading "+currentSection.lowercase().replaceFirstChar{it.uppercase()}+"…");Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val out=parsePlaylist(txt).filter{(filter==null||it.group.contains(filter,true))&&(!favouritesOnly||fav.has(it.url))};runOnUiThread{channels=out;if(out.isEmpty()){showEmptyState(if(favouritesOnly)"FAVOURITES" else currentSection,if(favouritesOnly)"No favourites yet. Hold OK on a channel to add one." else "Nothing is available in this section right now.")}else{showList()}}}catch(e:Exception){runOnUiThread{showMessage(brandName,"Unable to load right now. Check the internet connection and try again.")}}}.start()}
 private fun showList(){
  screenMode="LIST"
  previewHandler.removeCallbacksAndMessages(null);previewPlayer?.release();previewPlayer=null
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
   text="Loading preview…"
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
   previewStatus.text="Loading preview…"
   previewStatus.visibility=View.VISIBLE
   previewPlayer?.release()
   previewPlayer=ExoPlayer.Builder(this).build().also{p->
    playerView.player=p
    p.volume=0f
    p.addListener(object:Player.Listener{
     override fun onPlaybackStateChanged(state:Int){
      if(state==Player.STATE_READY && previewPlayer===p)previewStatus.visibility=View.GONE
     }
     override fun onPlayerError(error:PlaybackException){
      if(previewPlayer===p){previewStatus.text="Temporarily unavailable • OK to try full screen";previewStatus.visibility=View.VISIBLE}
     }
    })
    p.setMediaItem(MediaItem.fromUri(channels[index].url))
    p.prepare()
    p.play()
    playerView.postDelayed({
     if(previewPlayer===p && p.playbackState!=Player.STATE_READY){previewStatus.text="Temporarily unavailable • OK to try full screen";previewStatus.visibility=View.VISIBLE}
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
    previewHandler.removeCallbacksAndMessages(null)
    if(hasFocus){
     current=i
     previewTitle.text=ch.name
     val guideNow=epgNow[ch.tvgId]
     val guideNext=epgNext[ch.tvgId]
     previewMeta.text=if(guideNow!=null)"NOW  •  $guideNow"+(if(guideNext!=null)"\nNEXT •  $guideNext" else "") else (if(ch.group.isBlank())"GREEK TV" else ch.group.uppercase())+"   •   LIVE NOW"
     previewHandler.postDelayed({if(v.hasFocus())startPreview(i)},350)
    }
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
 private fun showMiniGuide(){
  val g=miniGuide?:return
  g.visibility=View.VISIBLE
  g.alpha=0f
  g.animate().alpha(1f).setDuration(140).start()
  g.removeCallbacks(hideMiniGuide)
  g.postDelayed(hideMiniGuide,5000)
 }
 private val hideMiniGuide=Runnable{miniGuide?.animate()?.alpha(0f)?.setDuration(180)?.withEndAction{miniGuide?.visibility=View.GONE}?.start()}
 private fun play(i:Int){
  previewHandler.removeCallbacksAndMessages(null)
  previewPlayer?.release();previewPlayer=null
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  current=i
  prefs.edit().putString("last_channel",channels[i].url).apply()
  recordRecent(channels[i])
  player?.release()
  player=ExoPlayer.Builder(this).build()
  var loadingView:TextView?=null
  player!!.addListener(object:Player.Listener{
   override fun onPlaybackStateChanged(state:Int){
    runOnUiThread{
     loadingView?.visibility=if(state==Player.STATE_BUFFERING||state==Player.STATE_IDLE)View.VISIBLE else View.GONE
     loadingView?.text=if(state==Player.STATE_BUFFERING)"Buffering channel…" else "Opening channel…"
    }
   }
   override fun onRenderedFirstFrame(){runOnUiThread{loadingView?.visibility=View.GONE}}
   override fun onPlayerError(error:PlaybackException){
    runOnUiThread{
     Toast.makeText(this@MainActivity,"Το κανάλι δεν είναι διαθέσιμο. Δοκιμάστε άλλο.",Toast.LENGTH_LONG).show()
     showList()
    }
   }
  })

  val frame=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
  val v=PlayerView(this).apply{
   player=this@MainActivity.player
   useController=false
   setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
   keepScreenOn=true
   setBackgroundColor(Color.BLACK)
  }
  frame.addView(v,FrameLayout.LayoutParams(-1,-1))
  loadingView=TextView(this).apply{
   text="Opening channel…";textSize=15f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER
   setPadding(22,12,22,12)
   background=GradientDrawable().apply{setColor(Color.argb(220,4,18,31));cornerRadius=16f;setStroke(1,Color.argb(105,145,205,245))}
  }
  frame.addView(loadingView,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))

  val ch=channels[i]
  val guide=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL
   gravity=Gravity.CENTER_VERTICAL
   setPadding(24,18,26,18)
   background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(244,5,20,35),Color.argb(236,8,42,70),Color.argb(220,4,14,27))).apply{
    cornerRadius=18f
    setStroke(1,Color.argb(105,175,215,245))
   }
   elevation=18f
  }
  val logoUrl=channelLogoUrl(ch)
  if(logoUrl.isNotBlank()){
   val logo=ImageView(this).apply{
    scaleType=ImageView.ScaleType.CENTER_INSIDE
    setPadding(5,5,5,5)
    background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=10f}
   }
   guide.addView(logo,LinearLayout.LayoutParams(64,50).apply{setMargins(0,0,18,0)})
   loadImageInto(logo,logoUrl)
  }else{
   guide.addView(TextView(this).apply{
    text="TV";gravity=Gravity.CENTER;textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)
    background=GradientDrawable().apply{setColor(Color.rgb(20,95,180));cornerRadius=10f}
   },LinearLayout.LayoutParams(64,50).apply{setMargins(0,0,18,0)})
  }

  val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  info.addView(TextView(this).apply{
   text=ch.name;textSize=21f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);setSingleLine(true)
  })
  val nowTitle=epgNow[ch.tvgId]
  val nextTitle=epgNext[ch.tvgId]
  info.addView(TextView(this).apply{
   text=if(nowTitle!=null)"NOW  •  $nowTitle" else (if(ch.group.isBlank())"LIVE TV" else ch.group.uppercase())
   textSize=14f;setTextColor(Color.rgb(210,228,243));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END
  })
  info.addView(TextView(this).apply{
   text=if(nextTitle!=null)"NEXT •  $nextTitle" else "Live channel"
   textSize=12f;setTextColor(Color.rgb(145,190,225));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,3,0,0)
  })
  guide.addView(info,LinearLayout.LayoutParams(0,-2,1f))

  val right=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.END}
  right.addView(TextView(this).apply{
   text=SimpleDateFormat("hh:mm a",Locale.US).format(Date());textSize=18f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.END
  })
  right.addView(TextView(this).apply{
   text=(if(fav.has(ch.url))"★ Favourite" else "☆ Add favourite")+"   •   ▲▼ Change channel"
   textSize=11f;setTextColor(Color.rgb(173,205,230));gravity=Gravity.END;setPadding(0,4,0,0)
  })
  guide.addView(right,LinearLayout.LayoutParams(-2,-2).apply{setMargins(24,0,0,0)})
  frame.addView(guide,FrameLayout.LayoutParams(-1,-2,Gravity.START or Gravity.BOTTOM).apply{setMargins(34,0,34,34)})
  miniGuide=guide
  setContentView(frame)
  showMiniGuide()

  player!!.setMediaItem(MediaItem.fromUri(ch.url))
  player!!.prepare()
  player!!.play()
 }
 override fun onKeyDown(k:Int,e:KeyEvent?):Boolean{
  if(player!=null&&channels.isNotEmpty()){
   when(k){
    KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_INFO,KeyEvent.KEYCODE_MENU->{showMiniGuide();return true}
    KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_CHANNEL_UP->{play((current+1)%channels.size);return true}
    KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_CHANNEL_DOWN->{play((current-1+channels.size)%channels.size);return true}
    KeyEvent.KEYCODE_STAR,KeyEvent.KEYCODE_BOOKMARK->{
     fav.toggle(channels[current].url)
     Toast.makeText(this,if(fav.has(channels[current].url))"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show()
     play(current)
     return true
    }
    KeyEvent.KEYCODE_BACK->{player?.release();player=null;miniGuide=null;showList();return true}
   }
  }
  if(k==KeyEvent.KEYCODE_BACK&&screenMode!="HOME"){showHome();return true}
  return super.onKeyDown(k,e)
 }
 private fun showLoadingState(message:String){
  screenMode="LOADING"
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(50,40,50,40)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(5,27,47),Color.rgb(2,9,17)))
  }
  root.addView(ProgressBar(this).apply{isIndeterminate=true},LinearLayout.LayoutParams(64,64))
  root.addView(TextView(this).apply{text=message;textSize=20f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,22,0,5)})
  root.addView(TextView(this).apply{text="Greek One is getting things ready";textSize=12f;setTextColor(Color.rgb(135,181,214));gravity=Gravity.CENTER})
  setContentView(root)
 }
 private fun showEmptyState(title:String,message:String){
  screenMode="LIST"
  val root=shell(title)
  root.gravity=Gravity.CENTER_HORIZONTAL
  root.addView(TextView(this).apply{text="◌";textSize=48f;gravity=Gravity.CENTER;setTextColor(accent);setPadding(0,70,0,14)})
  root.addView(TextView(this).apply{text=message;textSize=20f;gravity=Gravity.CENTER;setTextColor(Color.rgb(202,218,232));setPadding(40,0,40,28)})
  root.addView(button("←  Back to Home"){showHome()},LinearLayout.LayoutParams(360,72))
  setContentView(root)
 }
 private fun installAppUpdate(url:String){
  if(isPappas){openUri(url);return}
  try{
   val dm=getSystemService(DOWNLOAD_SERVICE) as DownloadManager
   val req=DownloadManager.Request(Uri.parse(url)).apply{
    setTitle("Greek One update")
    setDescription("Downloading the latest Greek One update…")
    setMimeType("application/vnd.android.package-archive")
    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    setAllowedOverMetered(true)
    setAllowedOverRoaming(true)
   }
   val id=dm.enqueue(req)
   Toast.makeText(this,"Downloading Greek One update…",Toast.LENGTH_LONG).show()
   Thread{
    var done=false
    var failed=false
    repeat(180){
     if(done||failed)return@repeat
     try{
      dm.query(DownloadManager.Query().setFilterById(id)).use{cur->
       if(cur.moveToFirst()){
        when(cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))){
         DownloadManager.STATUS_SUCCESSFUL->done=true
         DownloadManager.STATUS_FAILED->failed=true
        }
       }
      }
     }catch(_:Exception){}
     if(!done&&!failed)Thread.sleep(1000)
    }
    runOnUiThread{
     if(done){
      val apkUri=dm.getUriForDownloadedFile(id)
      if(apkUri!=null){
       val intent=Intent(Intent.ACTION_VIEW).apply{
        setDataAndType(apkUri,"application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
       }
       try{startActivity(intent)}catch(_:Exception){showMessage("Greek One","The update downloaded, but Android could not open the installer. Open Downloads and select the Greek One update.")}
      }else showMessage("Greek One","The update downloaded, but Android could not open the installer.")
     }else{
      showMessage("Greek One","The update could not be downloaded. Please try again.")
     }
    }
   }.start()
  }catch(_:Exception){
   showMessage("Greek One","The update could not be started. Please try again.")
  }
 }
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage(brandName,"Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
