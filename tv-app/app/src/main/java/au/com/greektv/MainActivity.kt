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
 private var homeWarmupStarted=false
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
  handleInstallStatus(intent)
  val cfgKey=if(isPappas)"papas_config" else "reskakis_config"
  try{remoteConfig=JSONObject(prefs.getString(cfgKey,"{}")?:"{}")}catch(_:Exception){}
  showLaunchScreen()
  refreshRemoteConfig()
  Handler(Looper.getMainLooper()).postDelayed({
   if(player==null){
    try{showHome()}catch(e:Throwable){
     android.util.Log.e("GreekOne","Home launch failed",e)
     showSafeHome()
    }
   }
  },650)
 }
 private fun showSafeHome(){
  screenMode="HOME"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  player?.release();player=null;previewPlayer?.release();previewPlayer=null
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(48,36,48,36)
   setBackgroundColor(Color.rgb(2,7,13))
  }
  root.addView(ImageView(this).apply{
   setImageResource(R.drawable.greek_one_mark);scaleType=ImageView.ScaleType.CENTER_INSIDE
  },LinearLayout.LayoutParams(220,220))
  root.addView(TextView(this).apply{
   text="GREEK ONE";textSize=34f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER
  })
  root.addView(TextView(this).apply{
   text="Greek Television";textSize=16f;setTextColor(Color.rgb(158,180,201));gravity=Gravity.CENTER;setPadding(0,6,0,28)
  })
  root.addView(button("Live TV"){loadChannels()},LinearLayout.LayoutParams(360,72).apply{setMargins(0,6,0,6)})
  root.addView(button("Retry Home"){try{showHome()}catch(_:Throwable){Toast.makeText(this@MainActivity,"Home screen could not load.",Toast.LENGTH_SHORT).show()}},LinearLayout.LayoutParams(360,72).apply{setMargins(0,6,0,6)})
  setContentView(root)
  root.post{if(root.childCount>3)root.getChildAt(3).requestFocus()}
 }
 private val prefs by lazy{getSharedPreferences("greek_tv",MODE_PRIVATE)}
 override fun onNewIntent(i:Intent){
  super.onNewIntent(i)
  setIntent(i)
  handleInstallStatus(i)
 }
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
  root.addView(settingCard("Check for update","Download and install the latest Greek One build"){
   AlertDialog.Builder(this)
    .setTitle("Check for update")
    .setMessage("Download the latest signed Greek One build now?")
    .setPositiveButton("Download"){_,_->installAppUpdate("https://rtv.up.railway.app")}
    .setNegativeButton("Cancel",null)
    .show()
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
  root.addView(TextView(this).apply{text="Greek One  •  Version $ver  •  Build ${currentVersionCode()}";textSize=10f;letterSpacing=.08f;gravity=Gravity.CENTER_HORIZONTAL;setTextColor(Color.rgb(91,137,170));setPadding(0,24,0,0)})
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
 private fun warmGreekOneHome(){
  if(isPappas||homeWarmupStarted||homeCachedChannels().isNotEmpty())return
  homeWarmupStarted=true
  Thread{
   try{
    val parsed=parsePlaylist(fetchPlaylist())
    runOnUiThread{
     homeWarmupStarted=false
     if(screenMode=="HOME"&&parsed.isNotEmpty())showGreekOneHome()
    }
   }catch(_:Exception){homeWarmupStarted=false}
  }.start()
 }
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
  if(channels.none{it.tvgId.isNotBlank()}){onDone?.invoke();return}
  val nowMs=System.currentTimeMillis()
  if(epgNow.isNotEmpty()&&nowMs-epgLoadedAt<epgCacheTtlMs){onDone?.invoke();return}
  if(epgLoading)return
  epgLoading=true
  epgNow.clear();epgNext.clear()

  fun normId(raw:String):String{
   var s=raw.trim().lowercase(Locale.ROOT)
   s=s.substringBefore("@")
   s=s.replace("skaitv.gr","skai.gr")
    .replace("alphatv.gr","alpha.gr")
    .replace("megachannel.gr","mega.gr")
    .replace("opentv.gr","open.gr")
    .replace("starchannel.gr","star.gr")
   return s.replace(Regex("[^a-z0-9α-ωάέήίόύώϊϋΐΰ.]"),"")
  }
  val wanted=channels.mapNotNull{ch->ch.tvgId.takeIf{it.isNotBlank()}?.let{normId(it) to ch.tvgId}}.toMap()

  fun parseGuide(stream:java.io.InputStream){
   val parser=Xml.newPullParser()
   parser.setInput(stream,"UTF-8")
   val now=System.currentTimeMillis()
   var event=parser.eventType
   while(event!=XmlPullParser.END_DOCUMENT){
    if(event==XmlPullParser.START_TAG&&parser.name=="programme"){
     val rawId=parser.getAttributeValue(null,"channel")?:""
     val canonical=wanted[normId(rawId)]
     val start=parseXmltvDate(parser.getAttributeValue(null,"start")?:"")
     val stop=parseXmltvDate(parser.getAttributeValue(null,"stop")?:"")
     var title=""
     var inner=parser.next()
     while(!(inner==XmlPullParser.END_TAG&&parser.name=="programme")){
      if(inner==XmlPullParser.START_TAG&&parser.name=="title")title=parser.nextText()
      inner=parser.next()
     }
     if(canonical!=null&&title.isNotBlank()){
      if(start<=now&&stop>now)epgNow[canonical]=title
      else if(start>now&&!epgNext.containsKey(canonical))epgNext[canonical]=title
     }
    }
    event=parser.next()
   }
  }

  Thread{
   try{
    val sources=listOf(
     "https://epgshare01.online/epgshare01/epg_ripper_GR1.xml.gz" to true,
     cfgString("epgUrl","https://iptv-org.github.io/epg/guides/gr/cosmote.gr.epg.xml") to false
    )
    for((url,gz) in sources){
     if(epgNow.isNotEmpty()||epgNext.isNotEmpty())break
     try{
      val conn=URL(url).openConnection().apply{connectTimeout=7000;readTimeout=15000}
      val base=conn.getInputStream()
      val input:java.io.InputStream=if(gz)java.util.zip.GZIPInputStream(base) else base
      input.use{parseGuide(it)}
     }catch(_:Exception){}
    }
   }finally{
    epgLoadedAt=System.currentTimeMillis()
    epgLoading=false
    runOnUiThread{onDone?.invoke()}
   }
  }.start()
 }
 private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=21f;gravity=Gravity.CENTER_VERTICAL;isAllCaps=false;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.WHITE);background=panel(card);isFocusable=true;setPadding(30,0,24,0);stateListAnimator=null;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,72).apply{setMargins(0,5,0,5)};setOnFocusChangeListener{v,f->background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(10,116,213),Color.rgb(28,151,245))).apply{cornerRadius=18f;setStroke(2,Color.argb(205,255,255,255))}else panel(card);v.animate().scaleX(if(f)1.035f else 1f).scaleY(if(f)1.035f else 1f).setDuration(145).start();v.elevation=if(f)16f else 1f}}
 private fun weatherName(code:Int)=when(code){
  0->"Clear";1,2->"Mostly clear";3->"Cloudy";45,48->"Fog";51,53,55,56,57->"Drizzle";61,63,65,66,67,80,81,82->"Rain";71,73,75,77,85,86->"Snow";95,96,99->"Storm";else->"Weather"
 }
 private fun timeAt(zone:String):String=SimpleDateFormat("hh:mm a",Locale.US).apply{timeZone=TimeZone.getTimeZone(zone)}.format(Date())
 private fun updateHomeHeader(){
  athensInfoView?.text="🇬🇷 ATHENS\n${timeAt("Europe/Athens")}  •  ${athensTemp}°C"
  sydneyInfoView?.text="🇦🇺 SYDNEY\n${timeAt("Australia/Sydney")}  •  ${sydneyTemp}°C"
  dateInfoView?.text=SimpleDateFormat("d MMM",Locale.getDefault()).format(Date()).uppercase()
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
 private fun loadOpenGraphArtwork(view:ImageView,pageUrl:String,fallback:String){
  Thread{
   try{
    val conn=URL(pageUrl).openConnection().apply{connectTimeout=5000;readTimeout=7000}
    conn.setRequestProperty("User-Agent","Mozilla/5.0 GreekOneTV/1.0")
    val html=conn.getInputStream().bufferedReader().use{it.readText()}
    val r1=Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""",RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)
    val r2=Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""",RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)
    val art=(r1?:r2?:fallback).replace("&amp;","&")
    val bmp=URL(art).openStream().use{BitmapFactory.decodeStream(it)}
    if(bmp!=null)runOnUiThread{view.setImageBitmap(bmp)} else runOnUiThread{loadImageInto(view,fallback)}
   }catch(_:Exception){runOnUiThread{loadImageInto(view,fallback)}}
  }.start()
 }
 private fun shell(title:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(64,34,64,28);setBackgroundColor(bg);addView(TextView(this@MainActivity).apply{text=title;textSize=34f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.05f;setPadding(6,0,0,2)});addView(TextView(this@MainActivity).apply{text="Η Ελλάδα στο σπίτι σας  •  $placeUpper → WORLD";textSize=15f;setTextColor(accent);letterSpacing=.03f;setPadding(7,0,0,22)})}


 private fun showPreloadedSeries(){
  if(isPappas){showHome();return}
  screenMode="PRELOADED_SERIES";activeHomeNav="Preloaded Series"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  previewPlayer?.release();previewPlayer=null;player?.release();player=null
  val root=shell("PRELOADED SERIES")
  root.addView(TextView(this).apply{text="Greek series library • official broadcaster archives";textSize=14f;setTextColor(muted);setPadding(7,0,0,14)})
  data class SeriesItem(val title:String,val source:String,val episodes:String,val url:String,val brousko:Boolean=false)
  val series=listOf(
   SeriesItem("ΑΓΙΟΣ ΠΑΪΣΙΟΣ – ΑΠΟ ΤΑ ΦΑΡΑΣΑ ΣΤΟΝ ΟΥΡΑΝΟ","MEGA","2 seasons • 21 episodes • complete","https://www.megatv.com/ekpompes/576225/agios-paisios-apo-ta-farasa-ston-ourano/"),
   SeriesItem("ΔΥΟ ΞΕΝΟΙ","MEGA","59 episodes","https://www.megatv.com/ekpompes/43265/duo-ksenoi/"),
   SeriesItem("ΕΘΝΙΚΗ ΕΛΛΑΔΟΣ","MEGA","15 episodes","https://www.megatv.com/ekpompes/1356374/ethniki-ellados/"),
   SeriesItem("ΕΙΜΑΣΤΕ ΣΤΟΝ ΑΕΡΑ","MEGA","51 episodes","https://www.megatv.com/ekpompes/43348/eimaste-ston-aera/"),
   SeriesItem("ΕΙΣΑΙ ΤΟ ΤΑΙΡΙ ΜΟΥ","MEGA","30 episodes","https://www.megatv.com/ekpompes/43098/eisai-to-tairi-mou-2/"),
   SeriesItem("ΕΜΕΙΣ ΚΑΙ ΕΜΕΙΣ","MEGA","139 episodes","https://www.megatv.com/ekpompes/42594/emeis-kai-emeis-2/"),
   SeriesItem("ΕΞΑΨΗ","MEGA","72 episodes","https://www.megatv.com/ekpompes/202020/eksapsi-nea-seira/"),
   SeriesItem("ΕΥΤΥΧΙΣΜΕΝΟΙ ΜΑΖΙ","MEGA","61 episodes","https://www.megatv.com/ekpompes/43235/eutuxismenoi-mazi/"),
   SeriesItem("Η ΓΕΝΙΑ ΤΩΝ 592€","MEGA","18 episodes • complete","https://www.megatv.com/tvshows/51758/epeisodio-1/"),
   SeriesItem("Η ΝΤΑΝΤΑ","MEGA","70 episodes","https://www.megatv.com/ekpompes/43294/i-ntanta/"),
   SeriesItem("ΚΩΝΣΤΑΝΤΙΝΟΥ ΚΑΙ ΕΛΕΝΗΣ","ANT1","official archive","https://www.antenna.gr/webtv/3142"),
   SeriesItem("ΛΑΤΡΕΜΕΝΟΙ ΜΟΥ ΓΕΙΤΟΝΕΣ","MEGA","53 episodes","https://www.megatv.com/ekpompes/42963/latremenoi-mou-geitones/"),
   SeriesItem("ΜΑΖΙ ΣΟΥ","MEGA","20 episodes","https://www.megatv.com/ekpompes/42996/mazi-sou-2/"),
   SeriesItem("ΜΑΥΡΑ ΜΕΣΑΝΥΧΤΑ","MEGA","48 episodes","https://www.megatv.com/ekpompes/43238/maura-mesanuxta/"),
   SeriesItem("ΜΕ ΤΑ ΠΑΝΤΕΛΟΝΙΑ ΚΑΤΩ","MEGA","34 episodes • complete","https://www.megatv.com/tvshows/52525/episode-001/"),
   SeriesItem("ΜΠΡΟΥΣΚΟ","ANT1","772 episodes","https://nkv.antenna.gr/minisites/brusco/videos",true),
   SeriesItem("ΝΤΟΛΤΣΕ ΒΙΤΑ","MEGA","70 episodes","https://www.megatv.com/ekpompes/43343/ntoltse-vita/"),
   SeriesItem("ΟΙ ΑΠΑΡΑΔΕΚΤΟΙ","MEGA","48 episodes","https://www.megatv.com/ekpompes/43303/aparadektoi/"),
   SeriesItem("ΠΕΙΡΑΣΜΟΣ","MEGA","20 episodes • complete","https://www.megatv.com/tvshows/45147/epeisodio-1/"),
   SeriesItem("ΠΕΝΗΝΤΑ ΠΕΝΗΝΤΑ","MEGA","81 episodes","https://www.megatv.com/ekpompes/43207/peninta-peninta/"),
   SeriesItem("ΠΕΡΙ ΑΝΕΜΩΝ ΚΑΙ ΥΔΑΤΩΝ","MEGA","95 episodes • finale","https://www.megatv.com/ekpompes/43241/peri-anemn-kai-udatn/"),
   SeriesItem("ΣΑΒΒΑΤΟΓΕΝΝΗΜΕΝΕΣ","MEGA","33 episodes","https://www.megatv.com/ekpompes/43202/savvatogennimenes/"),
   SeriesItem("ΣΤΟ ΠΑΡΑ ΠΕΝΤΕ","MEGA","complete archive","https://www.megatv.com/ekpompes/43346/sto-para-pente/"),
   SeriesItem("ΣΤΟΥΣ 31 ΔΡΟΜΟΥΣ","MEGA","12 episodes • complete","https://www.megatv.com/tvshows/49615/epeisodio-1/"),
   SeriesItem("ΣΧΕΔΟΝ ΕΝΗΛΙΚΕΣ","MEGA","12 episodes • complete","https://www.megatv.com/ekpompes/142326/sxedon-enilikes/"),
   SeriesItem("ΦΙΛΟΔΟΞΙΕΣ","MEGA","799 episodes","https://www.megatv.com/ekpompes/43252/filodoksies/"),
   SeriesItem("SAFE SEX","MEGA","43+ episode archive","https://www.megatv.com/ekpompes/43229/safe-sex/"),
   SeriesItem("SINGLES","MEGA","21 episodes • season 1","https://www.megatv.com/ekpompes/43287/singles/"),
   SeriesItem("SINGLES 2","MEGA","28 episodes • season 2","https://www.megatv.com/ekpompes/43292/singles-2/"),
   SeriesItem("SINGLES 3","MEGA","final season archive","https://www.megatv.com/ekpompes/42682/singles-3-2/"),
  )
  val scroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_NEVER}
  val grid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(2,0,10,20)}
  val fallbackArt=listOf(
   "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=900&q=82",
   "https://images.unsplash.com/photo-1485846234645-a62644f84728?auto=format&fit=crop&w=900&q=82",
   "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?auto=format&fit=crop&w=900&q=82",
   "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=900&q=82"
  )
  series.chunked(3).forEachIndexed{rowIndex,group->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;clipChildren=false;clipToPadding=false}
   group.forEachIndexed{col,s->
    val index=rowIndex*3+col
    val card=LinearLayout(this).apply{
     orientation=LinearLayout.VERTICAL;isFocusable=true;isClickable=true;clipToOutline=true;elevation=6f
     background=panel(Color.rgb(8,27,45),16f)
     setOnClickListener{if(s.brousko)showBrousko() else showSeriesWeb(s.title,s.url)}
     setOnFocusChangeListener{v,f->
      v.background=if(f)GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(7,97,184),Color.rgb(24,141,232))).apply{cornerRadius=16f;setStroke(3,Color.WHITE)}else panel(Color.rgb(8,27,45),16f)
      v.animate().scaleX(if(f)1.025f else 1f).scaleY(if(f)1.025f else 1f).setDuration(120).start();v.elevation=if(f)20f else 6f
     }
    }
    val art=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.rgb(14,43,66))}
    card.addView(art,LinearLayout.LayoutParams(-1,118))
    loadOpenGraphArtwork(art,s.url,fallbackArt[index%fallbackArt.size])
    val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(13,9,13,9)}
    copy.addView(TextView(this@MainActivity).apply{
     text=s.title;textSize=14.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
    },LinearLayout.LayoutParams(-1,0,1f))
    copy.addView(TextView(this@MainActivity).apply{
     text=s.source+"  •  "+s.episodes;textSize=9.5f;setTextColor(Color.rgb(159,204,232));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
    })
    card.addView(copy,LinearLayout.LayoutParams(-1,72))
    row.addView(card,LinearLayout.LayoutParams(0,190,1f).apply{setMargins(0,0,12,12)})
   }
   repeat(3-group.size){row.addView(View(this),LinearLayout.LayoutParams(0,190,1f).apply{setMargins(0,0,12,12)})}
   grid.addView(row,LinearLayout.LayoutParams(-1,190))
  }
  scroll.addView(grid)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(button("←  Home"){showGreekOneHome()},LinearLayout.LayoutParams(180,54).apply{setMargins(6,5,0,0)})
  setContentView(root)
  grid.post{
   if(grid.childCount>0){
    val firstRow=grid.getChildAt(0)
    if(firstRow is LinearLayout&&firstRow.childCount>0)firstRow.getChildAt(0).requestFocus()
   }
  }
 }
 private fun showSeriesWeb(title:String,url:String){
  screenMode="SERIES_WEB"
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
  val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(28,12,28,12);background=panel(Color.rgb(4,22,38),0f)}
  bar.addView(TextView(this).apply{text=title;textSize=22f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,54,1f))
  bar.addView(button("← Series"){showPreloadedSeries()},LinearLayout.LayoutParams(150,54))
  root.addView(bar,LinearLayout.LayoutParams(-1,78))
  val web=WebView(this).apply{
   setBackgroundColor(bg);isFocusable=true;isFocusableInTouchMode=true
   settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.mediaPlaybackRequiresUserGesture=false;settings.cacheMode=WebSettings.LOAD_DEFAULT
   settings.userAgentString=settings.userAgentString+" GreekOneTV/1.0"
   webViewClient=object:WebViewClient(){
    override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{
     val u=request?.url?.toString()?:"";val host=request?.url?.host?:""
     return !(request?.url?.scheme=="http"||request?.url?.scheme=="https")
    }
    override fun onReceivedError(view:WebView?,request:WebResourceRequest?,error:WebResourceError?){if(request?.isForMainFrame==true)Toast.makeText(this@MainActivity,"Series archive could not load.",Toast.LENGTH_SHORT).show()}
   }
  }
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);web.loadUrl(url);web.requestFocus()
 }

 private fun showBrousko(){
  if(isPappas){showHome();return}
  screenMode="BROUSKO"
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
  val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(28,12,28,12);background=panel(Color.rgb(4,22,38),0f)}
  bar.addView(TextView(this).apply{text="ΜΠΡΟΥΣΚΟ";textSize=24f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,54,1f))
  bar.addView(TextView(this).apply{text="ANT1 • Episodes 1–772";textSize=13f;setTextColor(muted);gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(220,54))
  bar.addView(button("← Series"){showPreloadedSeries()},LinearLayout.LayoutParams(150,54))
  root.addView(bar,LinearLayout.LayoutParams(-1,78))
  val web=WebView(this).apply{
   setBackgroundColor(bg);isFocusable=true;isFocusableInTouchMode=true
   settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.mediaPlaybackRequiresUserGesture=false;settings.cacheMode=WebSettings.LOAD_DEFAULT
   settings.userAgentString=settings.userAgentString+" GreekOneTV/1.0"
   webViewClient=object:WebViewClient(){
    override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{
     val u=request?.url?.toString()?:""
     val host=request?.url?.host?:""
     return !(request?.url?.scheme=="http"||request?.url?.scheme=="https")
    }
    override fun onReceivedError(view:WebView?,request:WebResourceRequest?,error:WebResourceError?){
     if(request?.isForMainFrame==true)Toast.makeText(this@MainActivity,"ANT1 archive could not load.",Toast.LENGTH_SHORT).show()
    }
   }
  }
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  web.loadUrl("https://nkv.antenna.gr/minisites/brusco/videos")
  web.requestFocus()
 }

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

  data class MovieItem(val title:String,val year:String,val meta:String,val url:String,val accent:Int,val posterUrl:String?=null)
  val movies=listOf(
   MovieItem("Our Guardian Angel","1961","Comedy • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M000545",Color.rgb(37,91,126),"https://m.media-amazon.com/images/M/MV5BODA0MjIzOGQtOTQ5OC00NzcxLWE1YTMtY2VkMzBiMzg4OTUyXkEyXkFqcGc@._V1_.jpg"),
   MovieItem("The Girl of the Neighborhood","1954","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M002260",Color.rgb(113,61,92),"https://m.media-amazon.com/images/M/MV5BZWE5MWNjNGUtNmM1OC00YTIzLTg4OGItNjY3Zjk0MjIyOTM5XkEyXkFqcGc@._V1_.jpg"),
   MovieItem("Me, Myself and I","1964","Greek Cinema • Comedy","https://live.ertflix.gr/details/ERT_214813",Color.rgb(43,96,129),"https://image.tmdb.org/t/p/w500/1JysIlTfbtQLCcIxo8l1NESQEzX.jpg"),
   MovieItem("Cry","1964","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M002490",Color.rgb(82,61,116),"https://www.filmy.gr/wp-content/uploads/2026/05/Cry-1964-50.jpg"),

   MovieItem("The Mischief-Makers","Classic","Comedy • Old Greek Cinema","https://live.ertflix.gr/details/ERT_213212",Color.rgb(127,79,31)),
   MovieItem("The Big Shark","1957","Comedy • Romance • Old Greek Cinema","https://live.ertflix.gr/details/ERT_182067",Color.rgb(23,104,120),"https://a.ltrbxd.com/resized/film-poster/3/3/6/8/3/0/336830-o-megalokarharias-0-230-0-345-crop.jpg?v=690a87308f"),
   MovieItem("Bouboulina","1959","Biography • Historical • Greek Cinema","https://live.ertflix.gr/details/ERT_P000052",Color.rgb(105,59,39),"https://m.media-amazon.com/images/M/MV5BNGI2NTAxMWUtZWI5Zi00ZTQxLThiMDEtYTU5NjNjYWE3Y2RkXkEyXkFqcGc@._V1_.jpg"),
   MovieItem("The Refugee","1969","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M001256",Color.rgb(45,76,118)),

   MovieItem("Athens – Istanbul","2008","Drama • Adventure • Greek Cinema","https://live.ertflix.gr/details/ERT_M002494",Color.rgb(26,99,119)),
   MovieItem("Almond Tree in Bloom","1959","Romance • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M000294",Color.rgb(118,70,52),"https://m.media-amazon.com/images/M/MV5BMWUwMDQ5MmMtMTcxZS00ZmFiLTkxZTQtNmI3MDAxMDNmNDk2XkEyXkFqcGc@._V1_.jpg"),
   MovieItem("Blood Ties","2012","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M001448",Color.rgb(104,49,68)),
   MovieItem("The Poor Boy","Classic","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M001392",Color.rgb(59,76,112)),

   MovieItem("My Poor Little Sparrow","Classic","Drama • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M002279",Color.rgb(126,64,86)),
   MovieItem("The Hook","1976","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000567",Color.rgb(67,61,113),"https://image.tmdb.org/t/p/w500/rkK4qXErHyXou729KVVL7bkrl8a.jpg"),
   MovieItem("Lefteris Dimakopoulos","1993","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000031",Color.rgb(39,82,117),"https://m.media-amazon.com/images/M/MV5BMGU3OWZkMzctZDI2Mi00YmZjLTg1MTYtODJjN2JiMDZhNDVlXkEyXkFqcGc@._V1_.jpg"),
   MovieItem("Exotic Vitamins","Classic","Comedy • Old Greek Cinema","https://live.ertflix.gr/details/ERT_M002272",Color.rgb(25,105,95)),

   MovieItem("Liubi","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000372",Color.rgb(81,53,112)),
   MovieItem("Roza of Smyrna","2016","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000440",Color.rgb(118,53,61),"https://media.interactive.netuse.gr/filesystem/images/20161221/low/pegasus_LARGE_t_1581_107302039.JPG"),
   MovieItem("The King","2002","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000029",Color.rgb(56,75,112),"https://m.media-amazon.com/images/M/MV5BZGNjNjU2OTMtMzVkMy00MGFkLWFmZGQtM2EwMGFlMGUzZTI3XkEyXkFqcGc@._V1_FMjpg_UX1000_.jpg"),
   MovieItem("The Seventh Sun of Love","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000886",Color.rgb(111,60,85)),

   MovieItem("Drift","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000807",Color.rgb(38,87,118)),
   MovieItem("Love Under the Date Tree","Greek Cinema","Romance • Greek Cinema","https://live.ertflix.gr/details/ERT_M000338",Color.rgb(122,55,82)),
   MovieItem("Invincible Lovers","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P001777",Color.rgb(47,74,113)),
   MovieItem("Such a Long Absence","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_P000437",Color.rgb(73,60,112)),

   MovieItem("The Photographers","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M002473",Color.rgb(31,92,120)),
   MovieItem("Young Aphrodites","1963","Drama • Arthouse • Greek Cinema","https://live.ertflix.gr/details/ERT_M002468",Color.rgb(93,56,131),"https://m.media-amazon.com/images/M/MV5BZWNjYjZiNjItMjZmOS00N2ZmLTg4NDktZGYxM2EyZGZiZDkxXkEyXkFqcGc@._V1_FMjpg_UX1000_.jpg"),
   MovieItem("Riviera","Greek Cinema","Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000893",Color.rgb(20,102,125)),
   MovieItem("Rembetiko","1983","Music • Drama • Greek Cinema","https://live.ertflix.gr/details/ERT_M000496",Color.rgb(83,47,105),"https://a.ltrbxd.com/resized/film-poster/1/7/6/7/9/17679-rembetiko-0-600-0-900-crop.jpg?v=d4070d253a"),

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
    elevation=6f;setOnClickListener{openBroadcasterContent(m.title,m.url,"MOVIE_WEB")}
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
   loadImageInto(posterImage,m.posterUrl?:fallbackArt)
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

 private fun showGreekCooking(){
  if(isPappas){showHome();return}
  screenMode="GREEK_COOKING"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  previewPlayer?.release();previewPlayer=null;player?.release();player=null
  activeHomeNav="ΕΛΛΗΝΙΚΗ ΜΑΓΕΙΡΙΚΗ"
  val root=shell("ΕΛΛΗΝΙΚΗ ΜΑΓΕΙΡΙΚΗ")
  root.addView(TextView(this).apply{text="Ελληνικές εκπομπές μαγειρικής • επίσημες πηγές";textSize=14f;setTextColor(Color.rgb(142,190,222));setPadding(6,16,6,18)})
  data class CookingShow(val title:String,val subtitle:String,val source:String,val url:String)
  val shows=listOf(
   CookingShow("ΠΟΠ Μαγειρική","Ανδρέας Λαγός • Ελληνικά προϊόντα & συνταγές","ERTFLIX","https://www.ertflix.gr/vod/vod.179854"),
   CookingShow("Kitchen Lab","Άκης Πετρετζίκης • Σεζόν 2026–2027","ΣΚΑΪ","https://www.skai.gr/tv/show/psuchagogia/kitchen-lab-2/sezon-2026-2027"),
   CookingShow("Kitchen Lab • 04/10/2026","Πλήρες επεισόδιο • 3 συνταγές","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/kitchen-lab-04102026"),
   CookingShow("Kitchen Lab • 03/10/2026","Πλήρες επεισόδιο • πρεμιέρα σεζόν","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/kitchen-lab-03102026"),
   CookingShow("Γεύσεις από Ελλάδα • Επ. 1","Χόρτα του χειμώνα • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=3dD9ps5DXWo"),
   CookingShow("Γεύσεις από Ελλάδα • Επ. 5","Πορτοκάλι • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=yUf0f6kXQNo"),
   CookingShow("Γεύσεις από Ελλάδα • Επ. 6","Θαλασσινά • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=X0cX9UyCFP8"),
   CookingShow("Γεύσεις από Ελλάδα • Επ. 7","Γάλα • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=FmpxTcbWVZs"),
   CookingShow("Γεύσεις από Ελλάδα • Επ. 8","Μανιτάρια • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=vCR3zTyEwho"),
   CookingShow("Γεύσεις από Ελλάδα • Επ. 9","Ελιά • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=ySshUw411xM"),
   CookingShow("Γεύσεις από Ελλάδα • Αβγό","17/03/2017 • Νίκος Καραθάνος","ΕΡΤ","https://www.youtube.com/watch?v=26_3zgdmqLs"),
   CookingShow("Kitchen Lab • Τονοσαλάτα με κουσκούς","Συνταγή από το επεισόδιο 04/10/2026","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/tonosalata-me-kouskous"),
   CookingShow("Kitchen Lab • Σπιτικός γύρος κοτόπουλο","Συνταγή από το επεισόδιο 04/10/2026","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/spitikos-guros-kotopoulo"),
   CookingShow("Kitchen Lab • Εύκολη lemon pie","Συνταγή από το επεισόδιο 04/10/2026","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/eukoli-lemon-pie"),
   CookingShow("Kitchen Lab • Pulled beef sandwich","Συνταγή από το επεισόδιο 03/10/2026","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/pulled-beef-sandwich"),
   CookingShow("Kitchen Lab • Ελληνική καρμπονάρα στον φούρνο","Συνταγή από το επεισόδιο 03/10/2026","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/elliniki-karmponara-ston-fourno"),
   CookingShow("Kitchen Lab • Ατομικά banoffee με peanut crumble","Συνταγή από το επεισόδιο 03/10/2026","ΣΚΑΪ","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/atomika-banoffe-me-peanut-crumble"),
   CookingShow("Γεύσεις από Ελλάδα • Χωρίς λάδι","12/04/2017 • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=Qof4R6vKE-w"),
   CookingShow("Γεύσεις από Ελλάδα • Πατάτα","07/04/2017 • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=XDl2O11UefI"),
   CookingShow("Γεύσεις από Ελλάδα • Μελιτζάνα","08/05/2017 • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=CDjYRygsyHg"),
   CookingShow("Γεύσεις από Ελλάδα • Μπρόκολο","06/04/2017 • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=Eue3NUL9350"),
   CookingShow("Γεύσεις από Ελλάδα • Καρότο","05/04/2017 • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=lljIV_Ud0vg"),
   CookingShow("Γεύσεις από Ελλάδα • Αγκινάρα","30/03/2017 • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=CAekUSA0Za0"),
   CookingShow("Γεύσεις από Ελλάδα • Κασέρι","10/05/2017 • πλήρες επεισόδιο","ΕΡΤ","https://www.youtube.com/watch?v=O9V6Oy71xDM"),
   CookingShow("Νηστικοί Πράκτορες • 31/10/2011","Ντίνα Νικολάου & Ιωσήφ Μαρινάκης","STAR","https://www.youtube.com/watch?v=ft57Ews96eM"),
   CookingShow("Μπουκιά και Συχώριο • Αθήνα Β’","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55708/athina-v/"),
   CookingShow("Μπουκιά και Συχώριο • Για ένα κομμάτι πίτα","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55852/gia-ena-kommati-pita/"),
   CookingShow("Μπουκιά και Συχώριο • Κωνσταντινούπολη","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55742/knstantinoupoli/"),
   CookingShow("Μπουκιά και Συχώριο • Βέροια – Νάουσα","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55736/veroia-naousa/"),
   CookingShow("Μπουκιά και Συχώριο • Σίφνος Α’","Η Σίφνος του Τσελεμεντέ","MEGA","https://www.megatv.com/gtvshows/55846/sifnos-a-i-sifnos-tou-tselemente/"),
   CookingShow("Μπουκιά και Συχώριο • Σίφνος Β’","Μια Κυκλαδίτισσα λωλή","MEGA","https://www.megatv.com/gtvshows/55850/sifnos-v-mia-kukladitissa-lli/"),
   CookingShow("Μπουκιά και Συχώριο • Ορεινή Κορινθία","Φενεός • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55966/oreini-korinthia-feneos/"),
   CookingShow("Μπουκιά και Συχώριο • Πάτμος","Το νησί της Αποκάλυψης","MEGA","https://www.megatv.com/gtvshows/55900/patmos-to-nisi-tis-apokaluis/"),
   CookingShow("Μπουκιά και Συχώριο • Λήμνος","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55784/limnos/"),
   CookingShow("Μπουκιά και Συχώριο • Κέρκυρα","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55774/kerkura/"),
   CookingShow("Μπουκιά και Συχώριο • Πάρος","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55732/paros/"),
   CookingShow("Μπουκιά και Συχώριο • Ήπειρος","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55744/ipeiros/"),
   CookingShow("Μπουκιά και Συχώριο • Κάλυμνος","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55942/kalumnos-2/"),
   CookingShow("Μπουκιά και Συχώριο • Σάμος","Η Κυρία των Αμπελιών","MEGA","https://www.megatv.com/gtvshows/55932/skiathos-sti-skia-tou-ath/"),
   CookingShow("Μπουκιά και Συχώριο • Σάμος 2","Στο Νησί του Πυθαγόρα","MEGA","https://www.megatv.com/gtvshows/55936/samos-2-sto-nisi-tou-puthagora/"),
   CookingShow("Μπουκιά και Συχώριο • Κύθνος","Με ανοιχτά πανιά για Κύθνο","MEGA","https://www.megatv.com/gtvshows/55798/me-anoixta-pania-gia-kuthno"),
   CookingShow("Μπουκιά και Συχώριο • Αργολίδα","Ηλίας Μαμαλάκης • επίσημο αρχείο","MEGA","https://www.megatv.com/gtvshows/55972/argolida/"),
   CookingShow("Μπουκιά και Συχώριο • Ζυμαρικά","Τα πολυαγαπημένα","MEGA","https://www.megatv.com/gtvshows/55822/zumarika-ta-poluagapimena/")
  )
  val scroll=ScrollView(this).apply{isFillViewport=true}
  val grid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(2,0,2,6)}

  fun artworkFor(m:CookingShow,index:Int):String{
   if(m.url.contains("youtube.com/watch")){
    val id=m.url.substringAfter("v=").substringBefore("&")
    if(id.isNotBlank())return "https://img.youtube.com/vi/$id/hqdefault.jpg"
   }
   return when{
    m.title.startsWith("Kitchen Lab")->listOf(
     "https://images.unsplash.com/photo-1556911220-bff31c812dba?auto=format&fit=crop&w=700&q=82",
     "https://images.unsplash.com/photo-1556910103-1c02745aae4d?auto=format&fit=crop&w=700&q=82"
    )[index%2]
    m.title.startsWith("ΠΟΠ")->"https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=700&q=82"
    m.title.startsWith("Μπουκιά")->listOf(
     "https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=700&q=82",
     "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=700&q=82",
     "https://images.unsplash.com/photo-1563379926898-05f4575a45d8?auto=format&fit=crop&w=700&q=82"
    )[index%3]
    else->listOf(
     "https://images.unsplash.com/photo-1476224203421-9ac39bcb3327?auto=format&fit=crop&w=700&q=82",
     "https://images.unsplash.com/photo-1507048331197-7d4ac70811cf?auto=format&fit=crop&w=700&q=82",
     "https://images.unsplash.com/photo-1498837167922-ddd27525d352?auto=format&fit=crop&w=700&q=82"
    )[index%3]
   }
  }

  shows.chunked(3).forEachIndexed{rowIndex,group->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;clipChildren=false;clipToPadding=false}
   group.forEachIndexed{column,m->
    val index=rowIndex*3+column
    val card=LinearLayout(this).apply{
     orientation=LinearLayout.VERTICAL
     isFocusable=true;isClickable=true;clipToOutline=true
     background=panel(Color.rgb(8,27,45),16f)
     elevation=6f
     setOnClickListener{if(m.url.contains("youtube.com",true)||m.url.contains("youtu.be",true))openYouTubeExternal(m.url) else openBroadcasterContent(m.title,m.url,"COOKING_WEB")}
     setOnFocusChangeListener{v,f->
      v.background=if(f)GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(7,97,184),Color.rgb(24,141,232))).apply{cornerRadius=16f;setStroke(3,Color.WHITE)}else panel(Color.rgb(8,27,45),16f)
      v.animate().scaleX(if(f)1.025f else 1f).scaleY(if(f)1.025f else 1f).setDuration(120).start()
      v.elevation=if(f)20f else 6f
     }
    }
    val image=ImageView(this).apply{
     scaleType=ImageView.ScaleType.CENTER_CROP
     setBackgroundColor(Color.rgb(14,43,66))
    }
    card.addView(image,LinearLayout.LayoutParams(-1,112))
    loadImageInto(image,artworkFor(m,index))

    val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(13,9,13,9)}
    copy.addView(TextView(this@MainActivity).apply{
     text=m.title;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)
     maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
    },LinearLayout.LayoutParams(-1,0,1f))
    copy.addView(TextView(this@MainActivity).apply{
     text=m.source+"  •  "+m.subtitle;textSize=9.5f;setTextColor(Color.rgb(159,204,232))
     maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
    })
    card.addView(copy,LinearLayout.LayoutParams(-1,72))
    row.addView(card,LinearLayout.LayoutParams(0,184,1f).apply{setMargins(0,0,12,12)})
   }
   repeat(3-group.size){row.addView(View(this),LinearLayout.LayoutParams(0,184,1f).apply{setMargins(0,0,12,12)})}
   grid.addView(row,LinearLayout.LayoutParams(-1,184))
  }
  scroll.addView(grid)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(button("←  Home"){showGreekOneHome()},LinearLayout.LayoutParams(320,62))
  setContentView(root)
  grid.post{
   if(grid.childCount>0){
    val firstRow=grid.getChildAt(0)
    if(firstRow is LinearLayout && firstRow.childCount>0)firstRow.getChildAt(0).requestFocus()
   }
  }
 }
 private fun showOnDemand(){
  activeHomeNav="On Demand"
  currentSection="ON DEMAND"
  loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")
 }

 private fun showCategories(){
  if(isPappas){showHome();return}
  screenMode="CATEGORIES";activeHomeNav="Categories"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  previewPlayer?.release();previewPlayer=null;player?.release();player=null
  val root=shell("CATEGORIES")
  root.addView(TextView(this).apply{text="Choose what you want to watch";textSize=14f;setTextColor(Color.rgb(142,190,222));setPadding(7,0,0,16)})
  val scroll=ScrollView(this).apply{isFillViewport=true}
  val grid=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(2,0,10,18)}
  val cats=listOf(
   Triple("▣  All Live TV","Every live Greek channel",Pair(Color.rgb(18,124,210),{loadChannels()})),
   Triple("ERT","ERT channels",Pair(Color.rgb(18,91,186),{loadChannels("ERT")})),
   Triple("NEWS","Greek news channels",Pair(Color.rgb(165,34,45),{loadChannels("ΕΙΔΗΣΕΙΣ")})),
   Triple("CYPRUS","Cyprus television",Pair(Color.rgb(20,121,127),{loadChannels("ΚΥΠΡΟΣ")})),
   Triple("REGIONAL","Regional Greece",Pair(Color.rgb(93,69,147),{loadChannels("ΠΕΡΙΦΕΡΕΙΑΚΑ")})),
   Triple("MUSIC","Greek music channels",Pair(Color.rgb(158,52,140),{loadChannels("ΕΛΛΗΝΙΚΗ ΜΟΥΣΙΚΗ")})),
   Triple("ORTHODOX","Orthodox television",Pair(Color.rgb(132,83,29),{loadChannels("ΟΡΘΟΔΟΞΙΑ")})),
   Triple("WORLD","Greek international",Pair(Color.rgb(40,104,126),{loadChannels("ΕΛΛΗΝΙΚΑ ΔΙΕΘΝΗ")})),
   Triple("MOVIES","Preloaded Greek cinema",Pair(Color.rgb(155,26,83),{showPreloadedMovies()})),
   Triple("SERIES","Greek series library",Pair(Color.rgb(95,19,160),{showPreloadedSeries()})),
   Triple("COOKING","Greek cooking shows",Pair(Color.rgb(225,124,5),{showGreekCooking()})),
   Triple("TV GUIDE","Live preview + Now & Next",Pair(Color.rgb(4,116,68),{showTvGuide()}))
  )
  cats.chunked(3).forEach{group->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   group.forEach{item->row.addView(tvCard(item.first,item.second,item.third.first,item.third.second),LinearLayout.LayoutParams(0,112,1f).apply{setMargins(0,0,14,14)})}
   repeat(3-group.size){row.addView(View(this),LinearLayout.LayoutParams(0,112,1f).apply{setMargins(0,0,14,14)})}
   grid.addView(row)
  }
  scroll.addView(grid);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(button("←  Home"){showGreekOneHome()},LinearLayout.LayoutParams(180,54))
  setContentView(root);grid.post{if(grid.childCount>0){val r=grid.getChildAt(0);if(r is LinearLayout&&r.childCount>0)r.getChildAt(0).requestFocus()}}
 }

 private fun showSearchScreen(){
  if(isPappas){showHome();return}
  screenMode="SEARCH";activeHomeNav="Search"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  previewPlayer?.release();previewPlayer=null;player?.release();player=null
  data class SearchItem(val title:String,val meta:String,val type:String,val url:String)
  val staticItems=listOf(
   SearchItem("Our Guardian Angel","1961 • Comedy • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000545"),
   SearchItem("The Girl of the Neighborhood","1954 • Drama • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M002260"),
   SearchItem("Me, Myself and I","1964 • Greek Cinema • Comedy","Movie","https://live.ertflix.gr/details/ERT_214813"),
   SearchItem("Cry","1964 • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M002490"),
   SearchItem("The Mischief-Makers","Classic • Comedy • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_213212"),
   SearchItem("The Big Shark","1957 • Comedy • Romance • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_182067"),
   SearchItem("Bouboulina","1959 • Biography • Historical • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_P000052"),
   SearchItem("The Refugee","1969 • Drama • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M001256"),
   SearchItem("Athens – Istanbul","2008 • Drama • Adventure • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M002494"),
   SearchItem("Almond Tree in Bloom","1959 • Romance • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000294"),
   SearchItem("Blood Ties","2012 • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M001448"),
   SearchItem("The Poor Boy","Classic • Drama • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M001392"),
   SearchItem("My Poor Little Sparrow","Classic • Drama • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M002279"),
   SearchItem("The Hook","1976 • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000567"),
   SearchItem("Lefteris Dimakopoulos","1993 • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_P000031"),
   SearchItem("Exotic Vitamins","Classic • Comedy • Old Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M002272"),
   SearchItem("Liubi","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_P000372"),
   SearchItem("Roza of Smyrna","2016 • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_P000440"),
   SearchItem("The King","2002 • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_P000029"),
   SearchItem("The Seventh Sun of Love","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000886"),
   SearchItem("Drift","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000807"),
   SearchItem("Love Under the Date Tree","Greek Cinema • Romance • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000338"),
   SearchItem("Invincible Lovers","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_P001777"),
   SearchItem("Such a Long Absence","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_P000437"),
   SearchItem("The Photographers","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M002473"),
   SearchItem("Young Aphrodites","1963 • Drama • Arthouse • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M002468"),
   SearchItem("Riviera","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000893"),
   SearchItem("Rembetiko","1983 • Music • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000496"),
   SearchItem("Crows","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M000345"),
   SearchItem("The Tears of the Mountain","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_M001148"),
   SearchItem("Meteor and Shadow","Greek Cinema • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_271968"),
   SearchItem("Coat Fitting","2006 • Drama • Greek Cinema","Movie","https://live.ertflix.gr/details/ERT_214863"),
   SearchItem("ΑΓΙΟΣ ΠΑΪΣΙΟΣ – ΑΠΟ ΤΑ ΦΑΡΑΣΑ ΣΤΟΝ ΟΥΡΑΝΟ","MEGA • 2 seasons • 21 episodes • complete","Series","https://www.megatv.com/ekpompes/576225/agios-paisios-apo-ta-farasa-ston-ourano/"),
   SearchItem("ΔΥΟ ΞΕΝΟΙ","MEGA • 59 episodes","Series","https://www.megatv.com/ekpompes/43265/duo-ksenoi/"),
   SearchItem("ΕΘΝΙΚΗ ΕΛΛΑΔΟΣ","MEGA • 15 episodes","Series","https://www.megatv.com/ekpompes/1356374/ethniki-ellados/"),
   SearchItem("ΕΙΜΑΣΤΕ ΣΤΟΝ ΑΕΡΑ","MEGA • 51 episodes","Series","https://www.megatv.com/ekpompes/43348/eimaste-ston-aera/"),
   SearchItem("ΕΙΣΑΙ ΤΟ ΤΑΙΡΙ ΜΟΥ","MEGA • 30 episodes","Series","https://www.megatv.com/ekpompes/43098/eisai-to-tairi-mou-2/"),
   SearchItem("ΕΜΕΙΣ ΚΑΙ ΕΜΕΙΣ","MEGA • 139 episodes","Series","https://www.megatv.com/ekpompes/42594/emeis-kai-emeis-2/"),
   SearchItem("ΕΞΑΨΗ","MEGA • 72 episodes","Series","https://www.megatv.com/ekpompes/202020/eksapsi-nea-seira/"),
   SearchItem("ΕΥΤΥΧΙΣΜΕΝΟΙ ΜΑΖΙ","MEGA • 61 episodes","Series","https://www.megatv.com/ekpompes/43235/eutuxismenoi-mazi/"),
   SearchItem("Η ΓΕΝΙΑ ΤΩΝ 592€","MEGA • 18 episodes • complete","Series","https://www.megatv.com/tvshows/51758/epeisodio-1/"),
   SearchItem("Η ΝΤΑΝΤΑ","MEGA • 70 episodes","Series","https://www.megatv.com/ekpompes/43294/i-ntanta/"),
   SearchItem("ΚΩΝΣΤΑΝΤΙΝΟΥ ΚΑΙ ΕΛΕΝΗΣ","ANT1 • official archive","Series","https://www.antenna.gr/webtv/3142"),
   SearchItem("ΛΑΤΡΕΜΕΝΟΙ ΜΟΥ ΓΕΙΤΟΝΕΣ","MEGA • 53 episodes","Series","https://www.megatv.com/ekpompes/42963/latremenoi-mou-geitones/"),
   SearchItem("ΜΑΖΙ ΣΟΥ","MEGA • 20 episodes","Series","https://www.megatv.com/ekpompes/42996/mazi-sou-2/"),
   SearchItem("ΜΑΥΡΑ ΜΕΣΑΝΥΧΤΑ","MEGA • 48 episodes","Series","https://www.megatv.com/ekpompes/43238/maura-mesanuxta/"),
   SearchItem("ΜΕ ΤΑ ΠΑΝΤΕΛΟΝΙΑ ΚΑΤΩ","MEGA • 34 episodes • complete","Series","https://www.megatv.com/tvshows/52525/episode-001/"),
   SearchItem("ΜΠΡΟΥΣΚΟ","ANT1 • 772 episodes","Series","https://nkv.antenna.gr/minisites/brusco/videos"),
   SearchItem("ΝΤΟΛΤΣΕ ΒΙΤΑ","MEGA • 70 episodes","Series","https://www.megatv.com/ekpompes/43343/ntoltse-vita/"),
   SearchItem("ΟΙ ΑΠΑΡΑΔΕΚΤΟΙ","MEGA • 48 episodes","Series","https://www.megatv.com/ekpompes/43303/aparadektoi/"),
   SearchItem("ΠΕΙΡΑΣΜΟΣ","MEGA • 20 episodes • complete","Series","https://www.megatv.com/tvshows/45147/epeisodio-1/"),
   SearchItem("ΠΕΝΗΝΤΑ ΠΕΝΗΝΤΑ","MEGA • 81 episodes","Series","https://www.megatv.com/ekpompes/43207/peninta-peninta/"),
   SearchItem("ΠΕΡΙ ΑΝΕΜΩΝ ΚΑΙ ΥΔΑΤΩΝ","MEGA • 95 episodes • finale","Series","https://www.megatv.com/ekpompes/43241/peri-anemn-kai-udatn/"),
   SearchItem("ΣΑΒΒΑΤΟΓΕΝΝΗΜΕΝΕΣ","MEGA • 33 episodes","Series","https://www.megatv.com/ekpompes/43202/savvatogennimenes/"),
   SearchItem("ΣΤΟ ΠΑΡΑ ΠΕΝΤΕ","MEGA • complete archive","Series","https://www.megatv.com/ekpompes/43346/sto-para-pente/"),
   SearchItem("ΣΤΟΥΣ 31 ΔΡΟΜΟΥΣ","MEGA • 12 episodes • complete","Series","https://www.megatv.com/tvshows/49615/epeisodio-1/"),
   SearchItem("ΣΧΕΔΟΝ ΕΝΗΛΙΚΕΣ","MEGA • 12 episodes • complete","Series","https://www.megatv.com/ekpompes/142326/sxedon-enilikes/"),
   SearchItem("ΦΙΛΟΔΟΞΙΕΣ","MEGA • 799 episodes","Series","https://www.megatv.com/ekpompes/43252/filodoksies/"),
   SearchItem("SAFE SEX","MEGA • 43+ episode archive","Series","https://www.megatv.com/ekpompes/43229/safe-sex/"),
   SearchItem("SINGLES","MEGA • 21 episodes • season 1","Series","https://www.megatv.com/ekpompes/43287/singles/"),
   SearchItem("SINGLES 2","MEGA • 28 episodes • season 2","Series","https://www.megatv.com/ekpompes/43292/singles-2/"),
   SearchItem("SINGLES 3","MEGA • final season archive","Series","https://www.megatv.com/ekpompes/42682/singles-3-2/"),
   SearchItem("ΠΟΠ Μαγειρική","ERTFLIX • Ανδρέας Λαγός • Ελληνικά προϊόντα & συνταγές","Cooking","https://www.ertflix.gr/vod/vod.179854"),
   SearchItem("Kitchen Lab","ΣΚΑΪ • Άκης Πετρετζίκης • Σεζόν 2026–2027","Cooking","https://www.skai.gr/tv/show/psuchagogia/kitchen-lab-2/sezon-2026-2027"),
   SearchItem("Kitchen Lab • 04/10/2026","ΣΚΑΪ • Πλήρες επεισόδιο • 3 συνταγές","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/kitchen-lab-04102026"),
   SearchItem("Kitchen Lab • 03/10/2026","ΣΚΑΪ • Πλήρες επεισόδιο • πρεμιέρα σεζόν","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/kitchen-lab-03102026"),
   SearchItem("Γεύσεις από Ελλάδα • Επ. 1","ΕΡΤ • Χόρτα του χειμώνα • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=3dD9ps5DXWo"),
   SearchItem("Γεύσεις από Ελλάδα • Επ. 5","ΕΡΤ • Πορτοκάλι • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=yUf0f6kXQNo"),
   SearchItem("Γεύσεις από Ελλάδα • Επ. 6","ΕΡΤ • Θαλασσινά • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=X0cX9UyCFP8"),
   SearchItem("Γεύσεις από Ελλάδα • Επ. 7","ΕΡΤ • Γάλα • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=FmpxTcbWVZs"),
   SearchItem("Γεύσεις από Ελλάδα • Επ. 8","ΕΡΤ • Μανιτάρια • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=vCR3zTyEwho"),
   SearchItem("Γεύσεις από Ελλάδα • Επ. 9","ΕΡΤ • Ελιά • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=ySshUw411xM"),
   SearchItem("Γεύσεις από Ελλάδα • Αβγό","ΕΡΤ • 17/03/2017 • Νίκος Καραθάνος","Cooking","https://www.youtube.com/watch?v=26_3zgdmqLs"),
   SearchItem("Kitchen Lab • Τονοσαλάτα με κουσκούς","ΣΚΑΪ • Συνταγή από το επεισόδιο 04/10/2026","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/tonosalata-me-kouskous"),
   SearchItem("Kitchen Lab • Σπιτικός γύρος κοτόπουλο","ΣΚΑΪ • Συνταγή από το επεισόδιο 04/10/2026","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/spitikos-guros-kotopoulo"),
   SearchItem("Kitchen Lab • Εύκολη lemon pie","ΣΚΑΪ • Συνταγή από το επεισόδιο 04/10/2026","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-04-16/eukoli-lemon-pie"),
   SearchItem("Kitchen Lab • Pulled beef sandwich","ΣΚΑΪ • Συνταγή από το επεισόδιο 03/10/2026","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/pulled-beef-sandwich"),
   SearchItem("Kitchen Lab • Ελληνική καρμπονάρα στον φούρνο","ΣΚΑΪ • Συνταγή από το επεισόδιο 03/10/2026","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/elliniki-karmponara-ston-fourno"),
   SearchItem("Kitchen Lab • Ατομικά banoffee με peanut crumble","ΣΚΑΪ • Συνταγή από το επεισόδιο 03/10/2026","Cooking","https://www.skai.gr/tv/episode/psuchagogia/kitchen-lab-2/2026-10-03-16/atomika-banoffe-me-peanut-crumble"),
   SearchItem("Γεύσεις από Ελλάδα • Χωρίς λάδι","ΕΡΤ • 12/04/2017 • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=Qof4R6vKE-w"),
   SearchItem("Γεύσεις από Ελλάδα • Πατάτα","ΕΡΤ • 07/04/2017 • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=XDl2O11UefI"),
   SearchItem("Γεύσεις από Ελλάδα • Μελιτζάνα","ΕΡΤ • 08/05/2017 • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=CDjYRygsyHg"),
   SearchItem("Γεύσεις από Ελλάδα • Μπρόκολο","ΕΡΤ • 06/04/2017 • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=Eue3NUL9350"),
   SearchItem("Γεύσεις από Ελλάδα • Καρότο","ΕΡΤ • 05/04/2017 • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=lljIV_Ud0vg"),
   SearchItem("Γεύσεις από Ελλάδα • Αγκινάρα","ΕΡΤ • 30/03/2017 • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=CAekUSA0Za0"),
   SearchItem("Γεύσεις από Ελλάδα • Κασέρι","ΕΡΤ • 10/05/2017 • πλήρες επεισόδιο","Cooking","https://www.youtube.com/watch?v=O9V6Oy71xDM"),
   SearchItem("Νηστικοί Πράκτορες • 31/10/2011","STAR • Ντίνα Νικολάου & Ιωσήφ Μαρινάκης","Cooking","https://www.youtube.com/watch?v=ft57Ews96eM"),
   SearchItem("Μπουκιά και Συχώριο • Αθήνα Β’","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55708/athina-v/"),
   SearchItem("Μπουκιά και Συχώριο • Για ένα κομμάτι πίτα","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55852/gia-ena-kommati-pita/"),
   SearchItem("Μπουκιά και Συχώριο • Κωνσταντινούπολη","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55742/knstantinoupoli/"),
   SearchItem("Μπουκιά και Συχώριο • Βέροια – Νάουσα","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55736/veroia-naousa/"),
   SearchItem("Μπουκιά και Συχώριο • Σίφνος Α’","MEGA • Η Σίφνος του Τσελεμεντέ","Cooking","https://www.megatv.com/gtvshows/55846/sifnos-a-i-sifnos-tou-tselemente/"),
   SearchItem("Μπουκιά και Συχώριο • Σίφνος Β’","MEGA • Μια Κυκλαδίτισσα λωλή","Cooking","https://www.megatv.com/gtvshows/55850/sifnos-v-mia-kukladitissa-lli/"),
   SearchItem("Μπουκιά και Συχώριο • Ορεινή Κορινθία","MEGA • Φενεός • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55966/oreini-korinthia-feneos/"),
   SearchItem("Μπουκιά και Συχώριο • Πάτμος","MEGA • Το νησί της Αποκάλυψης","Cooking","https://www.megatv.com/gtvshows/55900/patmos-to-nisi-tis-apokaluis/"),
   SearchItem("Μπουκιά και Συχώριο • Λήμνος","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55784/limnos/"),
   SearchItem("Μπουκιά και Συχώριο • Κέρκυρα","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55774/kerkura/"),
   SearchItem("Μπουκιά και Συχώριο • Πάρος","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55732/paros/"),
   SearchItem("Μπουκιά και Συχώριο • Ήπειρος","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55744/ipeiros/"),
   SearchItem("Μπουκιά και Συχώριο • Κάλυμνος","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55942/kalumnos-2/"),
   SearchItem("Μπουκιά και Συχώριο • Σάμος","MEGA • Η Κυρία των Αμπελιών","Cooking","https://www.megatv.com/gtvshows/55932/skiathos-sti-skia-tou-ath/"),
   SearchItem("Μπουκιά και Συχώριο • Σάμος 2","MEGA • Στο Νησί του Πυθαγόρα","Cooking","https://www.megatv.com/gtvshows/55936/samos-2-sto-nisi-tou-puthagora/"),
   SearchItem("Μπουκιά και Συχώριο • Κύθνος","MEGA • Με ανοιχτά πανιά για Κύθνο","Cooking","https://www.megatv.com/gtvshows/55798/me-anoixta-pania-gia-kuthno"),
   SearchItem("Μπουκιά και Συχώριο • Αργολίδα","MEGA • Ηλίας Μαμαλάκης • επίσημο αρχείο","Cooking","https://www.megatv.com/gtvshows/55972/argolida/"),
   SearchItem("Μπουκιά και Συχώριο • Ζυμαρικά","MEGA • Τα πολυαγαπημένα","Cooking","https://www.megatv.com/gtvshows/55822/zumarika-ta-poluagapimena/")
  )
  var liveChannels=emptyList<Channel>()
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(66,30,66,30)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(5,25,44),Color.rgb(2,9,17)))
  }
  val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  title.addView(TextView(this).apply{text="SEARCH";textSize=32f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)})
  title.addView(TextView(this).apply{text="Channels, movies, series and cooking";textSize=12.5f;setTextColor(Color.rgb(129,190,235))})
  head.addView(title,LinearLayout.LayoutParams(0,-2,1f));head.addView(button("← Home"){showGreekOneHome()},LinearLayout.LayoutParams(150,54))
  root.addView(head,LinearLayout.LayoutParams(-1,76))
  val input=EditText(this).apply{
   hint="Type a channel, movie, series or cooking title…";textSize=20f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(135,166,190))
   isSingleLine=true;setPadding(20,0,20,0)
   background=GradientDrawable().apply{setColor(Color.argb(220,3,23,40));cornerRadius=16f;setStroke(2,Color.argb(110,125,190,230))}
  }
  root.addView(input,LinearLayout.LayoutParams(-1,62).apply{setMargins(0,0,0,14)})
  val count=TextView(this).apply{text="Start typing to search";textSize=12f;setTextColor(Color.rgb(155,190,216));setPadding(4,0,0,8)}
  root.addView(count)
  val scroll=ScrollView(this);val results=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(results);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  fun render(qRaw:String){
   val q=qRaw.trim();results.removeAllViews()
   if(q.isBlank()){count.text="Start typing to search";return}
   val found=mutableListOf<Pair<String,()->Unit>>()
   liveChannels.filter{it.name.contains(q,true)||it.group.contains(q,true)}.take(20).forEach{ch->
    found.add(("LIVE  •  "+ch.name+"\n"+ch.group) to {channels=liveChannels;val i=channels.indexOfFirst{x->x.url==ch.url};if(i>=0)play(i)})
   }
   staticItems.filter{it.title.contains(q,true)||it.meta.contains(q,true)||it.type.contains(q,true)}.take(30).forEach{item->
    found.add((item.type.uppercase()+"  •  "+item.title+"\n"+item.meta) to {
     when(item.type){
      "Movie"->openBroadcasterContent(item.title,item.url,"MOVIE_WEB")
      "Cooking"->if(item.url.contains("youtube.com",true)||item.url.contains("youtu.be",true))openYouTubeExternal(item.url) else openBroadcasterContent(item.title,item.url,"COOKING_WEB")
      else->if(item.title.contains("ΜΠΡΟΥΣΚΟ",true))showBrousko() else showSeriesWeb(item.title,item.url)
     }
    })
   }
   count.text=found.size.toString()+" result"+if(found.size==1)"" else "s"
   found.take(40).forEach{pair->
    val parts=pair.first.split("\n",limit=2)
    val row=LinearLayout(this).apply{
     orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true;setPadding(18,10,18,10)
     background=panel(Color.rgb(9,31,51),14f);setOnClickListener{pair.second()}
     setOnFocusChangeListener{v,f->v.background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(9,105,200),Color.rgb(30,160,240))).apply{cornerRadius=14f;setStroke(2,Color.WHITE)}else panel(Color.rgb(9,31,51),14f)}
    }
    val tw=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    tw.addView(TextView(this@MainActivity).apply{text=parts[0];textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
    tw.addView(TextView(this@MainActivity).apply{text=parts.getOrElse(1){""};textSize=10.5f;setTextColor(Color.rgb(156,194,222));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
    row.addView(tw,LinearLayout.LayoutParams(0,-2,1f));row.addView(TextView(this).apply{text="›";textSize=26f;setTextColor(Color.rgb(86,195,255))})
    results.addView(row,LinearLayout.LayoutParams(-1,66).apply{setMargins(0,0,0,7)})
   }
  }
  input.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){render(s?.toString()?:"")}
   override fun afterTextChanged(s:android.text.Editable?){}
  })
  setContentView(root);input.requestFocus()
  Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val parsed=parsePlaylist(txt);runOnUiThread{liveChannels=parsed;render(input.text.toString())}}catch(_:Exception){} }.start()
 }
 private fun showGreekOneHome(){
  screenMode="HOME"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null);player?.release();player=null;previewPlayer?.release();previewPlayer=null
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

  val root=FrameLayout(this).apply{setBackgroundColor(bg)}
  val backdrop=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;alpha=.74f;setBackgroundColor(Color.rgb(2,8,15))}
  root.addView(backdrop,FrameLayout.LayoutParams(-1,-1))
  val hero=cfgString("heroUrl",if(isPappas)"https://commons.wikimedia.org/wiki/Special:Redirect/file/Nafplio_from_Palamidi_castle.jpg" else "https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=1600&q=88")
  imageCache.get(hero)?.let{backdrop.setImageBitmap(it)}?:Thread{try{
   val bmp=URL(hero).openStream().use{BitmapFactory.decodeStream(it)}
   if(bmp!=null)imageCache.put(hero,bmp)
   runOnUiThread{if(bmp!=null)backdrop.setImageBitmap(bmp)}
  }catch(_:Exception){}}.start()
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.argb(28,1,6,12),Color.argb(132,1,8,15),Color.argb(248,1,7,13)))},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,intArrayOf(Color.argb(58,36,143,214),Color.argb(16,36,143,214),Color.TRANSPARENT))},FrameLayout.LayoutParams(-1,200))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(120,0,8,18),Color.TRANSPARENT))},FrameLayout.LayoutParams(430,-1))

  val lowerVeil=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(95,1,8,15),Color.argb(185,1,7,13)))}
  root.addView(lowerVeil,FrameLayout.LayoutParams(-1,-1).apply{topMargin=150})
  val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(70,22,70,28);clipToPadding=false}

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
    text="GREEK ONE";textSize=24f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.02f;setSingleLine(true)
   })
   bt.addView(TextView(this@MainActivity).apply{
    text="GREEK TELEVISION  •  CHIOS → WORLD";textSize=8.5f;letterSpacing=.11f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.rgb(145,195,229));setSingleLine(true)
   })
   brand.addView(bt,LinearLayout.LayoutParams(190,-2))
   top.addView(brand,LinearLayout.LayoutParams(0,-2,1.25f))
   fun infoChip():TextView=TextView(this).apply{
    textSize=10.2f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER
    setPadding(8,6,8,6)
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(190,4,22,39),Color.argb(170,8,48,79))).apply{cornerRadius=15f;setStroke(1,Color.argb(80,144,203,242))}
   }
   athensInfoView=infoChip()
   dateInfoView=TextView(this).apply{
    textSize=10f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.rgb(222,235,245));gravity=Gravity.CENTER;letterSpacing=.05f;setSingleLine(true)
   }
   sydneyInfoView=infoChip()
   top.addView(athensInfoView,LinearLayout.LayoutParams(178,60).apply{setMargins(6,0,4,0)})
   top.addView(dateInfoView,LinearLayout.LayoutParams(88,60))
   top.addView(sydneyInfoView,LinearLayout.LayoutParams(182,60).apply{setMargins(4,0,4,0)})
   val settingsChip=TextView(this).apply{
    text="⚙";textSize=22f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;isFocusable=true;isClickable=true
    background=GradientDrawable().apply{setColor(Color.argb(165,5,24,42));cornerRadius=14f;setStroke(1,Color.argb(75,150,205,245))}
    setOnClickListener{activeHomeNav="Settings";showSettings()}
    setOnFocusChangeListener{v,f->v.background=GradientDrawable().apply{setColor(if(f)Color.rgb(12,120,210) else Color.argb(165,5,24,42));cornerRadius=14f;setStroke(if(f)2 else 1,if(f)Color.WHITE else Color.argb(75,150,205,245))}}
   }
   val liveStatus=TextView(this).apply{
    text="●  LIVE";textSize=9.2f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(255,129,139));gravity=Gravity.CENTER
    background=GradientDrawable().apply{setColor(Color.argb(145,34,8,14));cornerRadius=13f;setStroke(1,Color.argb(110,255,95,105))}
   }
   top.addView(liveStatus,LinearLayout.LayoutParams(70,38).apply{setMargins(2,0,8,0)})
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
   else GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(252,2,12,24),Color.argb(246,3,30,52))).apply{
    cornerRadius=26f;setStroke(1,Color.argb(118,103,181,231))
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
  addNav("◷","Continue"){loadLastChannel()}
  addNav("◆","Preloaded Movies"){showPreloadedMovies()}
  addNav("▤","On Demand"){showOnDemand()}
  if(isPappas)addNav("●",placeName){loadChannels(placeFilter)}
  addNav("▥","Preloaded Series"){showPreloadedSeries()}
  addNav("☷","Categories"){showCategories()}
  addNav("▦","TV Guide"){showTvGuide()}
  if(!isPappas)addNav("▶","YouTube"){showYouTubeSearch()}
  addNav("⌕","Search"){showSearchScreen()}
  addNav("⚙","Settings"){showSettings()}

  navItems.forEachIndexed{i,item->
   val icon=item.first;val label=item.second;val action=item.third
   val selected=!isPappas&&label==activeHomeNav
   val row=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
    setPadding(10,0,12,0)
    layoutParams=LinearLayout.LayoutParams(-1,if(isPappas)50 else 43).apply{setMargins(0,1,0,1)}
    background=if(selected)GradientDrawable().apply{
     setColor(Color.argb(76,25,142,220));cornerRadius=15f;setStroke(1,Color.argb(80,83,184,238))
    }else GradientDrawable().apply{setColor(Color.argb(24,7,42,67));cornerRadius=15f}
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
    text=label;textSize=12.8f;typeface=Typeface.create("sans-serif-medium",if(selected)Typeface.BOLD else Typeface.NORMAL)
    setTextColor(if(selected)Color.WHITE else Color.rgb(210,226,237));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END
   }
   row.addView(labelView,LinearLayout.LayoutParams(0,-1,1f))
   row.setOnFocusChangeListener{v,f->
    row.background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,104,198),Color.rgb(31,177,246))).apply{
     cornerRadius=13f;setStroke(2,Color.argb(245,232,250,255))
    }else if(selected)GradientDrawable().apply{setColor(Color.argb(76,25,142,220));cornerRadius=13f}
     else GradientDrawable().apply{setColor(Color.argb(24,7,42,67));cornerRadius=15f}
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

  body.addView(nav,LinearLayout.LayoutParams(if(isPappas)224 else 286,-1).apply{
   setMargins(0,6,if(isPappas)18 else 22,0)
  })

  val mainScroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_NEVER}
  val main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(2,0,10,30);clipToPadding=false}
  mainScroll.addView(main,ViewGroup.LayoutParams(-1,-2))
  fun sectionTitle(t:String,sub:String=""){
   val wrap=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,14,0,8)}
   wrap.addView(View(this).apply{
    background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(74,207,255),Color.rgb(16,112,223))).apply{cornerRadius=3f}
   },LinearLayout.LayoutParams(5,28).apply{setMargins(0,0,10,0)})
   val tw=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   tw.addView(TextView(this@MainActivity).apply{
    text=t;textSize=21f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);setShadowLayer(6f,0f,2f,Color.argb(120,0,0,0))
   })
   if(sub.isNotBlank())tw.addView(TextView(this@MainActivity).apply{
    text=sub;textSize=10f;setTextColor(Color.rgb(130,183,220));setPadding(0,1,0,0)
   })
   wrap.addView(tw,LinearLayout.LayoutParams(0,-2,1f))
   main.addView(wrap)
  }
  if(!isPappas){
   val featured=homeCachedChannels().firstOrNull{it.name.contains("ERT 1",true)||it.name.contains("ERT1",true)}?:homeCachedChannels().firstOrNull()?:Channel("ERT 1 HD","","Greek TV","ERT1.gr")
   run{
    val heroCard=FrameLayout(this).apply{
     isFocusable=true;isClickable=true;clipToOutline=true;elevation=14f
     background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(248,3,18,32),Color.argb(235,7,68,111),Color.argb(205,4,28,50))).apply{
      cornerRadius=24f;setStroke(1,Color.argb(145,142,213,255))
     }
    }
    val glow=View(this).apply{
     background=GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,intArrayOf(Color.argb(105,34,191,255),Color.argb(20,34,191,255),Color.TRANSPARENT))
    }
    heroCard.addView(glow,FrameLayout.LayoutParams(-1,-1))
    val heroInner=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(24,16,22,16)}
    val logoWrap=FrameLayout(this).apply{
     background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=18f;setStroke(1,Color.argb(90,120,170,210))}
     elevation=8f
    }
    val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(12,12,12,12)}
    logoWrap.addView(logo,FrameLayout.LayoutParams(-1,-1))
    heroInner.addView(logoWrap,LinearLayout.LayoutParams(108,86).apply{setMargins(0,0,22,0)})
    val featuredLogo=channelLogoUrl(featured)
    if(featuredLogo.isNotBlank())loadImageInto(logo,featuredLogo) else logo.setImageResource(R.drawable.greek_one_mark)

    val heroText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    val livePill=TextView(this).apply{
     text="●  LIVE NOW";textSize=9.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER
     background=GradientDrawable().apply{setColor(Color.rgb(202,36,48));cornerRadius=11f};setPadding(10,3,10,3)
    }
    heroText.addView(livePill,LinearLayout.LayoutParams(-2,28))
    heroText.addView(TextView(this@MainActivity).apply{
     this.text=featured.name;textSize=25f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);setPadding(0,5,0,0)
    })
    val metaRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    listOf("LIVE","GREEK TV","HD").forEachIndexed{i,label->
     metaRow.addView(TextView(this@MainActivity).apply{
      text=label;textSize=8.8f;typeface=Typeface.DEFAULT_BOLD;setTextColor(if(i==0)Color.rgb(255,110,118) else Color.rgb(202,226,242));gravity=Gravity.CENTER
      background=GradientDrawable().apply{setColor(Color.argb(135,7,22,38));cornerRadius=9f;setStroke(1,Color.argb(75,145,205,245))}
      setPadding(9,3,9,3)
     },LinearLayout.LayoutParams(-2,25).apply{setMargins(0,3,7,3)})
    }
    heroText.addView(metaRow)
    heroText.addView(TextView(this@MainActivity).apply{
     this.text=epgNow[featured.tvgId]?.let{"NOW  •  "+it}?:"Greek television streaming live"
     textSize=14f;setTextColor(Color.rgb(231,242,250));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END
    })
    heroText.addView(TextView(this@MainActivity).apply{
     this.text=epgNext[featured.tvgId]?.let{"NEXT •  "+it}?:"CHIOS → WORLD  •  Press OK to watch"
     textSize=11.5f;setTextColor(Color.rgb(155,204,235));setPadding(0,3,0,0)
    })
    heroInner.addView(heroText,LinearLayout.LayoutParams(0,-2,1f))
    val watch=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
    watch.addView(TextView(this).apply{
     text="WATCH  ▶";textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER
     background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,119,221),Color.rgb(36,194,255))).apply{cornerRadius=15f}
    },LinearLayout.LayoutParams(140,50))
    watch.addView(TextView(this).apply{text="Full screen";textSize=9f;setTextColor(Color.rgb(166,205,232));gravity=Gravity.CENTER;setPadding(0,5,0,0)})
    heroInner.addView(watch,LinearLayout.LayoutParams(158,-2))
    heroCard.addView(heroInner,FrameLayout.LayoutParams(-1,-1))
    heroCard.setOnClickListener{if(featured.url.isNotBlank())playRecent(featured.url) else loadChannels("ERT")}
    heroCard.setOnFocusChangeListener{v,f->
     v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(4,Color.WHITE);cornerRadius=24f}else null
     v.animate().scaleX(if(f)1.014f else 1f).scaleY(if(f)1.014f else 1f).setDuration(145).start();v.elevation=if(f)24f else 14f
    }
    main.addView(heroCard,LinearLayout.LayoutParams(-1,132).apply{setMargins(0,4,0,10)})

    val quick=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    val quickData=listOf(
     Triple("▣  WATCH LIVE","All Greek channels",{loadChannels()}),
     Triple("◆  MOVIES","32 Greek films",{showPreloadedMovies()}),
     Triple("▥  SERIES","30 Greek series",{showPreloadedSeries()}),
     Triple("🍳  COOKING","43 shows & recipes",{showGreekCooking()}),
     Triple("▦  TV GUIDE","Live preview + Now/Next",{showTvGuide()}),
     Triple("⌕  SEARCH","Search everything",{showSearchScreen()})
    )
    quickData.forEachIndexed{i,item->
     val base=when(i){0->Color.rgb(11,100,183);1->Color.rgb(132,26,76);2->Color.rgb(83,29,143);3->Color.rgb(191,101,8);4->Color.rgb(8,103,73);else->Color.rgb(54,72,110)}
     quick.addView(tvCard(item.first,item.second,base,item.third),LinearLayout.LayoutParams(0,86,1f).apply{setMargins(0,0,10,0)})
    }
    main.addView(quick,LinearLayout.LayoutParams(-1,86).apply{setMargins(0,0,0,4)})

    sectionTitle("Featured Collections","Curated for Greek One")
    val featuredCollections=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    featuredCollections.addView(
     imageCard("Greek Cinema","32 films • classics to modern","https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1200&q=88"){showPreloadedMovies()},
     LinearLayout.LayoutParams(0,160,1f).apply{setMargins(0,0,12,0)}
    )
    featuredCollections.addView(
     imageCard("Greek Series","30 shows • broadcaster archives","https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=1200&q=88"){showPreloadedSeries()},
     LinearLayout.LayoutParams(0,160,1f).apply{setMargins(0,0,12,0)}
    )
    featuredCollections.addView(
     imageCard("Greek Cooking","43 shows & recipes","https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1200&q=88"){showGreekCooking()},
     LinearLayout.LayoutParams(0,160,1f).apply{setMargins(0,0,12,0)}
    )
    featuredCollections.addView(
     imageCard("Live TV Guide","Live preview • Now & Next","https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=88"){showTvGuide()},
     LinearLayout.LayoutParams(0,160,1f)
    )
    main.addView(featuredCollections)
   }
  }

  sectionTitle("Popular Greek Channels","One click to watch")
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
  val channelGlass=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(10,10,10,10)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(175,3,18,32),Color.argb(150,8,38,62))).apply{
    cornerRadius=18f;setStroke(1,Color.argb(72,116,184,229))
   }
  }
  channelGlass.addView(channelRow)
  main.addView(channelGlass)

  sectionTitle("Tonight on Greek One","Live, cinema and Greek favourites")
  val tonightRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val tonightData=listOf(
   arrayOf("Live from Greece","Greek TV • watch now","https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=88","LIVE"),
   arrayOf("Greek Cinema","32 films • classics & modern","https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1200&q=88","MOVIES"),
   arrayOf("Series Night","30 Greek series","https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=1200&q=88","SERIES"),
   arrayOf("Taste of Greece","43 cooking shows & recipes","https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1200&q=88","COOKING")
  )
  tonightData.forEach{item->
   val action:()->Unit=when(item[3]){
    "LIVE"->{ {loadChannels()} }
    "MOVIES"->{ {showPreloadedMovies()} }
    "SERIES"->{ {showPreloadedSeries()} }
    else->{ {showGreekCooking()} }
   }
   tonightRow.addView(imageCard(item[0],item[1],item[2],action),LinearLayout.LayoutParams(0,176,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(tonightRow)

  sectionTitle("Continue Watching","Pick up where you left off")
  val cont=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  run{
   val recent=try{JSONArray(prefs.getString("recent_channels","[]")?:"[]")}catch(_:Exception){JSONArray()}
   if(recent.length()==0){
    val cardView=imageCard("Start watching","Your recent channels will appear here","https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1200&q=85"){loadChannels()}
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
  val continueGlass=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(10,10,10,10)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(158,3,17,30),Color.argb(138,8,31,52))).apply{
    cornerRadius=18f;setStroke(1,Color.argb(65,106,173,218))
   }
  }
  continueGlass.addView(cont)
  main.addView(continueGlass)

  sectionTitle("Explore Greek One","Movies, series, cooking and live TV")
  val cats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val categoryData=mutableListOf<Array<String>>(
   arrayOf("▣  Greek TV","All Greek Channels",Color.rgb(18,124,210).toString()),
   arrayOf("◉  Preloaded Movies","Greek Cinema Library",Color.rgb(155,26,83).toString()),
   arrayOf("▦  TV Guide","Now & Next",Color.rgb(4,116,68).toString()),
   arrayOf("🍳  ΕΛΛΗΝΙΚΗ ΜΑΓΕΙΡΙΚΗ","Greek Cooking",Color.rgb(225,124,5).toString()),
   arrayOf("▥  Preloaded Series","Greek Series Library",Color.rgb(95,19,160).toString())
  )
  if(isPappas)categoryData.add(4,arrayOf("◉  $placeName","Local Content",Color.rgb(6,132,153).toString()))
  categoryData.forEach{a->
   val action:()->Unit=when{
    a[0].contains("Greek TV")->{ {loadChannels()} }
    a[0].contains("Movies")->{ {showPreloadedMovies()} }
    a[0].contains("Series")->{ {showPreloadedSeries()} }
    a[0].contains("TV Guide")->{ {showTvGuide()} }
    a[0].contains("ΜΑΓΕΙΡΙΚΗ")->{ {showGreekCooking()} }
    a[0].contains(placeName)->{ {loadChannels(placeFilter)} }
    else->{ {showCategories()} }
   }
   cats.addView(tvCard(a[0],a[1],a[2].toInt(),action).apply{gravity=Gravity.CENTER_VERTICAL;elevation=4f},LinearLayout.LayoutParams(0,96,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(cats)

  sectionTitle("Live Now","Direct channel access")
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

  val signature=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(18,12,18,12)
   background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(205,4,22,38),Color.argb(175,8,53,84),Color.argb(205,4,22,38))).apply{
    cornerRadius=18f;setStroke(1,Color.argb(85,114,188,235))
   }
  }
  signature.addView(ImageView(this).apply{setImageResource(R.drawable.greek_one_mark);scaleType=ImageView.ScaleType.CENTER_INSIDE},LinearLayout.LayoutParams(46,46).apply{setMargins(0,0,12,0)})
  val sigText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  sigText.addView(TextView(this@MainActivity).apply{text="GREEK ONE";textSize=16f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.04f})
  sigText.addView(TextView(this@MainActivity).apply{text="Greek television • cinema • series • cooking • live guide";textSize=10.5f;setTextColor(Color.rgb(151,199,230))})
  signature.addView(sigText,LinearLayout.LayoutParams(0,-2,1f))
  signature.addView(TextView(this).apply{text="CHIOS → WORLD";textSize=10f;letterSpacing=.12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(93,205,255));gravity=Gravity.CENTER_VERTICAL})
  main.addView(signature,LinearLayout.LayoutParams(-1,70).apply{setMargins(0,18,0,8)})

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
  warmGreekOneHome()
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
  addNav("▥","Preloaded Series"){showPreloadedSeries()}
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
   arrayOf("🍳  ΕΛΛΗΝΙΚΗ ΜΑΓΕΙΡΙΚΗ","Greek Cooking",Color.rgb(225,124,5).toString()),
   arrayOf("▥  Preloaded Series","Greek Series Library",Color.rgb(95,19,160).toString())
  )
  if(isPappas)categoryData.add(4,arrayOf("◉  $placeName","Local Content",Color.rgb(6,132,153).toString()))
  categoryData.forEach{a->
   val action:()->Unit=when{
    a[0].contains("Greek TV")->{ {loadChannels()} }
    a[0].contains("Movies")->{ {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")} }
    a[0].contains("TV Guide")->{ {showTvGuide()} }
    a[0].contains("ΜΑΓΕΙΡΙΚΗ")->{ {showGreekCooking()} }
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
   addView(TextView(this@MainActivity).apply{text=title;textSize=13.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
   if(subtitle.isNotBlank())addView(TextView(this@MainActivity).apply{text=subtitle;textSize=9.5f;setTextColor(Color.rgb(218,228,238));setPadding(0,2,0,0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
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
  textWrap.addView(TextView(this).apply{text=title;textSize=15.5f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);setShadowLayer(8f,0f,2f,Color.BLACK)})
  textWrap.addView(TextView(this).apply{text=subtitle;textSize=10f;setTextColor(Color.rgb(210,226,238))})
  frame.addView(textWrap,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  frame.setOnClickListener{action()}
  frame.setOnFocusChangeListener{v,f->v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(4,Color.WHITE);cornerRadius=14f}else null;v.animate().scaleX(if(f)1.065f else 1f).scaleY(if(f)1.065f else 1f).translationZ(if(f)11f else 0f).setDuration(145).start();v.elevation=if(f)18f else 3f}
  return frame
 }
 private fun showTvGuide(){
  screenMode="GUIDE"
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  player?.release();player=null;previewPlayer?.release();previewPlayer=null
  currentSection="TV GUIDE"

  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(42,26,42,26)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(2,8,15),Color.rgb(4,20,35),Color.rgb(2,8,15)))
  }
  val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val titleWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleWrap.addView(TextView(this).apply{text="TV GUIDE";textSize=34f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE)})
  titleWrap.addView(TextView(this).apply{text="$brandName  •  LIVE NOW & NEXT";textSize=12f;setTextColor(accent);letterSpacing=.055f})
  header.addView(titleWrap,LinearLayout.LayoutParams(0,-2,1f))
  header.addView(TextView(this).apply{text="▲▼ Browse   •   OK Watch   •   BACK Home";textSize=13f;setTextColor(muted)})
  root.addView(header,LinearLayout.LayoutParams(-1,72))

  val status=TextView(this).apply{
   text="Loading live programme information…";textSize=13f;setTextColor(Color.rgb(194,214,232));setPadding(14,7,14,7)
   background=GradientDrawable().apply{setColor(Color.argb(105,18,55,84));cornerRadius=12f;setStroke(1,Color.argb(70,120,180,230))}
  }
  root.addView(status,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,2,0,12)})

  val content=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val listPane=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(4,17,30),Color.rgb(8,29,48))).apply{cornerRadius=18f;setStroke(1,Color.argb(88,120,180,230))}
   setPadding(10,10,10,10);elevation=7f
  }
  val scroll=ScrollView(this)
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  scroll.addView(list)
  listPane.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))

  val previewPane=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,0,0,0)}
  val videoFrame=FrameLayout(this).apply{
   background=GradientDrawable().apply{setColor(Color.BLACK);cornerRadius=18f;setStroke(2,Color.argb(125,120,185,235))}
   clipToOutline=true;elevation=9f
  }
  val playerView=PlayerView(this).apply{useController=false;keepScreenOn=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);setBackgroundColor(Color.BLACK)}
  videoFrame.addView(playerView,FrameLayout.LayoutParams(-1,-1))
  videoFrame.addView(TextView(this).apply{
   text="LIVE";textSize=12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER
   background=GradientDrawable().apply{setColor(Color.rgb(205,34,52));cornerRadius=10f};setPadding(14,4,14,4)
  },FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply{setMargins(16,16,0,0)})
  val previewStatus=TextView(this).apply{
   text="Select a channel";textSize=13f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
   background=GradientDrawable().apply{setColor(Color.argb(210,5,18,31));cornerRadius=12f};setPadding(18,10,18,10)
  }
  videoFrame.addView(previewStatus,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
  previewPane.addView(videoFrame,LinearLayout.LayoutParams(-1,0,1f))

  val previewTitle=TextView(this).apply{text="Select a channel";textSize=26f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);setPadding(0,14,0,0)}
  val previewNow=TextView(this).apply{text="NOW  •  Loading…";textSize=15f;setTextColor(Color.WHITE);setPadding(0,5,0,0)}
  val previewNext=TextView(this).apply{text="NEXT •  Loading…";textSize=12.5f;setTextColor(Color.rgb(150,190,220));setPadding(0,3,0,0)}
  previewPane.addView(previewTitle);previewPane.addView(previewNow);previewPane.addView(previewNext)
  previewPane.addView(TextView(this).apply{text="Press OK for full-screen viewing.";textSize=12f;setTextColor(muted);setPadding(0,8,0,0)})

  fun startPreview(index:Int){
   if(index !in channels.indices)return
   current=index
   val ch=channels[index]
   previewTitle.text=ch.name
   previewNow.text="NOW  •  "+(epgNow[ch.tvgId]?:"Live programming")
   previewNext.text="NEXT •  "+(epgNext[ch.tvgId]?:"Schedule unavailable")
   previewStatus.text="Opening live preview…";previewStatus.visibility=View.VISIBLE
   previewHandler.removeCallbacksAndMessages(null)
   previewHandler.postDelayed({
    previewPlayer?.release()
    previewPlayer=ExoPlayer.Builder(this).build().also{p->
     playerView.player=p;p.volume=0f
     p.addListener(object:Player.Listener{
      override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY&&previewPlayer===p)previewStatus.visibility=View.GONE}
      override fun onPlayerError(error:PlaybackException){if(previewPlayer===p){previewStatus.text="Preview unavailable • OK to try full screen";previewStatus.visibility=View.VISIBLE}}
     })
     p.setMediaItem(MediaItem.fromUri(ch.url));p.prepare();p.play()
    }
   },300)
  }

  fun render(){
   list.removeAllViews()
   val guideChannels=channels.filter{it.tvgId.isNotBlank()}.ifEmpty{channels}
   status.text=if(epgNow.isEmpty()&&epgNext.isEmpty())
    "Live preview available • programme data is still loading or unavailable for some channels"
   else "Live programme information • "+epgNow.size+" channels with current programme data"

   guideChannels.forEach{ch->
    val realIndex=channels.indexOf(ch)
    val row=LinearLayout(this).apply{
     orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
     setPadding(14,7,12,7);background=panel(Color.rgb(10,31,50),12f)
    }
    val logoUrl=channelLogoUrl(ch)
    if(logoUrl.isNotBlank()){
     val logo=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(3,3,3,3);background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=9f}}
     row.addView(logo,LinearLayout.LayoutParams(48,38).apply{setMargins(0,0,12,0)});loadImageInto(logo,logoUrl)
    }else{
     row.addView(TextView(this).apply{text="TV";gravity=Gravity.CENTER;textSize=11f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(20,95,180));cornerRadius=9f}},LinearLayout.LayoutParams(48,38).apply{setMargins(0,0,12,0)})
    }
    val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    copy.addView(TextView(this@MainActivity).apply{text=ch.name;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END})
    copy.addView(TextView(this@MainActivity).apply{text="NOW  •  "+(epgNow[ch.tvgId]?:"Live");textSize=11f;setTextColor(Color.rgb(174,205,229));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,2,0,0)})
    row.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
    row.setOnClickListener{previewPlayer?.release();previewPlayer=null;if(realIndex>=0)play(realIndex)}
    row.setOnFocusChangeListener{v,f->
     v.background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(10,116,213),Color.rgb(26,150,245))).apply{cornerRadius=12f;setStroke(2,Color.WHITE)}else panel(Color.rgb(10,31,50),12f)
     v.animate().scaleX(if(f)1.012f else 1f).scaleY(if(f)1.012f else 1f).setDuration(90).start()
     if(f&&realIndex>=0)startPreview(realIndex)
    }
    list.addView(row,LinearLayout.LayoutParams(-1,64).apply{setMargins(0,0,0,7)})
   }
   list.post{if(list.childCount>0)list.getChildAt(0).requestFocus()}
  }

  content.addView(listPane,LinearLayout.LayoutParams(0,-1,.43f).apply{setMargins(0,0,18,0)})
  content.addView(previewPane,LinearLayout.LayoutParams(0,-1,.57f))
  root.addView(content,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)

  Thread{try{
   val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e}
   val all=parsePlaylist(txt)
   runOnUiThread{
    channels=all
    render()
    loadEpg{if(screenMode=="GUIDE")render()}
   }
  }catch(_:Exception){runOnUiThread{status.text="Unable to load TV guide";render()}}}.start()
 }
 private fun openYouTubeExternal(url:String){
  val id=when{
   url.contains("youtube.com/watch")->url.substringAfter("v=").substringBefore("&")
   url.contains("youtu.be/")->url.substringAfter("youtu.be/").substringBefore("?")
   else->""
  }
  val attempts=mutableListOf<Intent>()
  if(id.isNotBlank()){
   attempts.add(Intent(Intent.ACTION_VIEW,Uri.parse("vnd.youtube:$id")).apply{setPackage("com.google.android.youtube.tv");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})
   attempts.add(Intent(Intent.ACTION_VIEW,Uri.parse("vnd.youtube:$id")).apply{setPackage("com.google.android.youtube");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})
  }
  attempts.add(Intent(Intent.ACTION_VIEW,Uri.parse(url)).apply{setPackage("com.google.android.youtube.tv");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})
  attempts.add(Intent(Intent.ACTION_VIEW,Uri.parse(url)).apply{setPackage("com.google.android.youtube");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})
  for(intent in attempts){
   try{startActivity(intent);return}catch(_:Exception){}
  }
  showLibraryWeb("YouTube",url,"COOKING_WEB")
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
   settings.mediaPlaybackRequiresUserGesture=false
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
  if(k==KeyEvent.KEYCODE_BACK&&screenMode=="MOVIE_WEB"){showPreloadedMovies();return true}
  if(k==KeyEvent.KEYCODE_BACK&&screenMode=="COOKING_WEB"){showGreekCooking();return true}
  if(k==KeyEvent.KEYCODE_BACK&&screenMode=="SERIES_WEB"){showPreloadedSeries();return true}
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
  if(android.os.Build.VERSION.SDK_INT>=26&&!packageManager.canRequestPackageInstalls()){
   try{
    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
    showMessage("Greek One","Allow Greek One to install updates, then return and press Check for update again.")
   }catch(_:Exception){showMessage("Greek One","Android blocked in-app installation. Use Downloader as a fallback.")}
   return
  }
  Toast.makeText(this,"Downloading Greek One update…",Toast.LENGTH_LONG).show()
  Thread{
   try{
    val fresh=url+(if(url.contains("?"))"&" else "?")+"t="+System.currentTimeMillis()
    val conn=URL(fresh).openConnection().apply{connectTimeout=10000;readTimeout=30000}
    val installer=packageManager.packageInstaller
    val params=android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply{setAppPackageName(packageName)}
    val sessionId=installer.createSession(params)
    val session=installer.openSession(sessionId)
    conn.getInputStream().use{input->session.openWrite("GreekOne-update.apk",0,-1).use{out->input.copyTo(out);session.fsync(out)}}
    val statusIntent=Intent(this,MainActivity::class.java).apply{action="au.com.greektv.INSTALL_STATUS";addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)}
    val piFlags=PendingIntent.FLAG_UPDATE_CURRENT or (if(android.os.Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0)
    val pending=PendingIntent.getActivity(this,8817,statusIntent,piFlags)
    session.commit(pending.intentSender);session.close()
   }catch(_:Exception){runOnUiThread{showMessage("Greek One","The update could not be installed automatically. Downloader is still available as a fallback.")}}
  }.start()
 }
 private fun handleInstallStatus(i:Intent?){
  if(i?.action!="au.com.greektv.INSTALL_STATUS")return
  when(i.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,android.content.pm.PackageInstaller.STATUS_FAILURE)){
   android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION->{
    @Suppress("DEPRECATION")
    val confirm=i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    if(confirm!=null)try{startActivity(confirm)}catch(_:Exception){}
   }
   android.content.pm.PackageInstaller.STATUS_SUCCESS->Toast.makeText(this,"Greek One updated successfully.",Toast.LENGTH_LONG).show()
   else->showMessage("Greek One",i.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)?:"Android could not complete the update.")
  }
 }
 private fun openBroadcasterContent(title:String,url:String,mode:String){
  val u=url.lowercase(Locale.ROOT)
  if(u.contains("ertflix.gr")){
   val packages=listOf("com.ertflix.app","t.yi.erthybrid")
   for(pkg in packages){
    try{
     val intent=Intent(Intent.ACTION_VIEW,Uri.parse(url)).apply{setPackage(pkg);addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}
     startActivity(intent)
     return
    }catch(_:Exception){}
   }
  }
  showLibraryWeb(title,url,mode)
 }
 private fun showLibraryWeb(title:String,url:String,mode:String){
  screenMode=mode
  previewHandler.removeCallbacksAndMessages(null);headerHandler.removeCallbacksAndMessages(null)
  player?.release();player=null;previewPlayer?.release();previewPlayer=null
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
  val bar=LinearLayout(this).apply{
   orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
   setPadding(28,10,28,10);background=panel(Color.rgb(4,22,38),0f)
  }
  bar.addView(TextView(this).apply{
   text=title;textSize=19f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE)
   maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
  },LinearLayout.LayoutParams(0,54,1f))
  val backLabel=if(mode=="MOVIE_WEB")"← Movies" else "← Cooking"
  bar.addView(button(backLabel){
   if(mode=="MOVIE_WEB")showPreloadedMovies() else showGreekCooking()
  },LinearLayout.LayoutParams(170,54))
  root.addView(bar,LinearLayout.LayoutParams(-1,74))

  val web=WebView(this).apply{
   setBackgroundColor(Color.BLACK);isFocusable=true;isFocusableInTouchMode=true
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   settings.mediaPlaybackRequiresUserGesture=true
   settings.cacheMode=WebSettings.LOAD_DEFAULT
   settings.loadsImagesAutomatically=true
   settings.allowContentAccess=true
   settings.allowFileAccess=false
   settings.userAgentString=settings.userAgentString+" GreekOneTV/1.0"
   webChromeClient=android.webkit.WebChromeClient()
   webViewClient=object:WebViewClient(){
    override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{
     val target=request?.url?.toString()?:return false
     val scheme=request.url?.scheme?:""
     return if(scheme=="http"||scheme=="https"){false}else{true}
    }
    override fun onReceivedError(view:WebView?,request:WebResourceRequest?,error:WebResourceError?){
     if(request?.isForMainFrame==true)Toast.makeText(this@MainActivity,"This title could not load from the broadcaster. Try another title.",Toast.LENGTH_LONG).show()
    }
   }
  }
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  web.loadUrl(url)
  web.requestFocus()
 }
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage(brandName,"Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
