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
 private val brandName get()=if(isPappas)"PAPAS TV" else "GREEK ONE"
 private val placeName get()=if(isPappas)"Nafplio" else "Chios"
 private val placeUpper get()=placeName.uppercase()
 private val placeFilter get()=if(isPappas)"ΝΑΥΠΛΙΟ" else "ΧΙΟΣ"
 private var player:ExoPlayer?=null;private var previewPlayer:ExoPlayer?=null;private var channels=listOf<Channel>();private var current=0;private var overlay:TextView?=null;private var miniGuide:View?=null;private var currentSection="LIVE TV"
 private var remoteConfig=JSONObject()
 private var remoteRefreshDone=false
 private var launchUpdateChecked=false
 private var screenMode="HOME"
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
   wrap.addView(ImageView(this).apply{setImageResource(R.drawable.greek_one_icon);scaleType=ImageView.ScaleType.CENTER_INSIDE},LinearLayout.LayoutParams(360,360))
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
 private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=21f;gravity=Gravity.CENTER_VERTICAL;isAllCaps=false;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.WHITE);background=panel(card);isFocusable=true;setPadding(30,0,24,0);stateListAnimator=null;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,72).apply{setMargins(0,5,0,5)};setOnFocusChangeListener{v,f->background=if(f)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(10,116,213),Color.rgb(28,151,245))).apply{cornerRadius=18f;setStroke(2,Color.argb(205,255,255,255))}else panel(card);v.animate().scaleX(if(f)1.035f else 1f).scaleY(if(f)1.035f else 1f).setDuration(110).start();v.elevation=if(f)16f else 1f}}
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
 private fun showHome(){
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
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,intArrayOf(Color.argb(118,246,106,28),Color.argb(42,248,149,65),Color.TRANSPARENT,Color.TRANSPARENT))},FrameLayout.LayoutParams(-1,230))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(120,0,8,18),Color.TRANSPARENT))},FrameLayout.LayoutParams(430,-1))

  val lowerVeil=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.TRANSPARENT,Color.argb(95,1,8,15),Color.argb(185,1,7,13)))}
  root.addView(lowerVeil,FrameLayout.LayoutParams(-1,-1).apply{topMargin=150})
  val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,16,12)}

  // Full-width premium masthead, matching the locked reference.
  val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val identity=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  if(isPappas){
   identity.addView(TextView(this).apply{
    text="🇬🇷";textSize=44f;gravity=Gravity.CENTER;background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(12,86,190),Color.rgb(8,55,132))).apply{cornerRadius=10f;setStroke(1,Color.argb(120,255,255,255))}
    elevation=8f
   },LinearLayout.LayoutParams(92,76).apply{setMargins(0,0,16,0)})
  }else{
   identity.addView(ImageView(this).apply{
    setImageResource(R.drawable.greek_one_icon);scaleType=ImageView.ScaleType.CENTER_INSIDE;setPadding(5,5,5,5)
    background=GradientDrawable().apply{setColor(Color.rgb(3,15,32));cornerRadius=10f;setStroke(1,Color.argb(120,255,255,255))}
    elevation=8f
   },LinearLayout.LayoutParams(92,76).apply{setMargins(0,0,16,0)})
  }
  val wordmark=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  wordmark.addView(TextView(this).apply{
   text=brandName;textSize=44f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.012f;setSingleLine(true);setShadowLayer(10f,0f,3f,Color.argb(110,0,0,0))
  })
  wordmark.addView(TextView(this).apply{
   text="GREEK TELEVISION  ·  $placeUpper  ·  AND MORE";textSize=10.5f;setTextColor(Color.rgb(235,240,247));letterSpacing=.10f;setSingleLine(true)
  })
  identity.addView(wordmark)
  top.addView(identity,LinearLayout.LayoutParams(0,-2,1f))
  if(isPappas){
   top.addView(TextView(this).apply{
    text=cfgString("tagline","From Nafplio to the World").replace(" to the World","\nto the World");textSize=25f;typeface=Typeface.create("cursive",Typeface.ITALIC);setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(18,0,34,0);setShadowLayer(8f,0f,3f,Color.argb(120,0,0,0))
   })
   top.addView(TextView(this).apply{
    text=SimpleDateFormat("HH:mm   |   EEE d MMM",Locale.getDefault()).format(Date())+"   ⚙";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL or Gravity.END;setSingleLine(true)
   })
  }else{
   fun infoChip():TextView=TextView(this).apply{
    textSize=12.5f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);gravity=Gravity.CENTER
    setPadding(18,9,18,9)
    background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(185,5,22,39),Color.argb(160,10,50,82))).apply{cornerRadius=18f;setStroke(1,Color.argb(85,150,205,245))}
    elevation=6f
   }
   athensInfoView=infoChip()
   dateInfoView=TextView(this).apply{
    textSize=12f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.rgb(226,236,245));gravity=Gravity.CENTER;letterSpacing=.06f
    setPadding(16,0,16,0)
   }
   sydneyInfoView=infoChip()
   top.addView(athensInfoView,LinearLayout.LayoutParams(250,76).apply{setMargins(12,0,8,0)})
   top.addView(dateInfoView,LinearLayout.LayoutParams(145,76))
   top.addView(sydneyInfoView,LinearLayout.LayoutParams(258,76).apply{setMargins(8,0,8,0)})
   top.addView(TextView(this).apply{
    text="⚙";textSize=24f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
    background=GradientDrawable().apply{setColor(Color.argb(150,5,24,42));cornerRadius=18f;setStroke(1,Color.argb(75,150,205,245))}
   },LinearLayout.LayoutParams(62,62).apply{gravity=Gravity.CENTER_VERTICAL})
   updateHomeHeader()
   refreshHomeWeather()
   headerHandler.post(headerTick)
  }
  page.addView(top,LinearLayout.LayoutParams(-1,124))

  val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}

  // Premium glass navigation rail.
  val nav=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(if(isPappas)10 else 12,if(isPappas)12 else 14,if(isPappas)10 else 12,10)
   background=if(isPappas)GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(242,1,11,22),Color.argb(226,3,22,39))).apply{cornerRadius=16f;setStroke(1,Color.argb(72,150,195,230))}
   else GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(252,1,10,22),Color.argb(244,3,26,48),Color.argb(250,2,13,29))).apply{cornerRadius=26f;setStroke(1,Color.argb(125,102,185,242))}
   elevation=if(isPappas)8f else 20f
  }
  if(!isPappas){
   nav.addView(TextView(this).apply{
    text="GREEK ONE";textSize=10.5f;letterSpacing=.18f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.rgb(151,216,255));gravity=Gravity.CENTER_VERTICAL;setPadding(16,0,0,6)
   },LinearLayout.LayoutParams(-1,30))
   nav.addView(View(this).apply{setBackgroundColor(Color.argb(65,110,185,235))},LinearLayout.LayoutParams(-1,1).apply{setMargins(8,0,8,8)})
  }
  val navItems=listOf<Pair<String,()->Unit>>(
   "⌂   Home" to {showHome()},"▣   Live TV" to {loadChannels()},"♡   Favourites" to {loadChannels(favouritesOnly=true)},"◷   Continue" to {loadLastChannel()},
   "▤   On Demand" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},"●   $placeName" to {loadChannels(placeFilter)},"◎   World TV" to {loadChannels("ΔΙΕΘΝΗ")},
   "☷   Categories" to {loadChannels()},"▦   TV Guide" to {showTvGuide()},"⌕   Search" to {loadChannels()},"⚙   Settings" to {showSettings()}
  )
  navItems.forEachIndexed{i,it->
   val nb=button(it.first,it.second).apply{
    textSize=if(isPappas)14f else 15f
    typeface=Typeface.create("sans-serif-medium",if(i==0)Typeface.BOLD else Typeface.NORMAL)
    setPadding(if(isPappas)16 else 18,0,8,0);setSingleLine(true)
    layoutParams=LinearLayout.LayoutParams(-1,if(isPappas)50 else 55).apply{setMargins(0,if(isPappas)2 else 4,0,if(isPappas)2 else 4)}
    background=if(i==0)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(7,113,210),Color.rgb(31,174,252))).apply{cornerRadius=if(isPappas)12f else 17f;setStroke(if(isPappas)1 else 2,Color.argb(235,236,249,255))}
    else GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(176,5,27,48),Color.argb(130,7,40,68))).apply{cornerRadius=17f;setStroke(1,Color.argb(72,123,192,239))}
    if(!isPappas)setOnFocusChangeListener{v,focused->
     background=if(focused)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(7,119,220),Color.rgb(39,184,255))).apply{cornerRadius=17f;setStroke(2,Color.WHITE)}
     else if(i==0)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(7,113,210),Color.rgb(31,174,252))).apply{cornerRadius=17f;setStroke(2,Color.argb(235,236,249,255))}
     else GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(176,5,27,48),Color.argb(130,7,40,68))).apply{cornerRadius=17f;setStroke(1,Color.argb(72,123,192,239))}
     v.animate().scaleX(if(focused)1.025f else 1f).scaleY(if(focused)1.025f else 1f).setDuration(100).start()
     v.elevation=if(focused)18f else 2f
    }
   }
   nav.addView(nb)
   if(!isPappas&&(i==3||i==8))nav.addView(View(this).apply{setBackgroundColor(Color.argb(45,120,185,230))},LinearLayout.LayoutParams(-1,1).apply{setMargins(12,4,12,4)})
  }
  body.addView(nav,LinearLayout.LayoutParams(if(isPappas)224 else 246,-1).apply{setMargins(0,8,if(isPappas)18 else 20,0)})

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
   val ch=homeChannel(a[1])
   val now=ch?.tvgId?.let{epgNow[it]}
   val next=ch?.tvgId?.let{epgNext[it]}
   val sub=when{now!=null&&next!=null->"NOW  •  $now\nNEXT •  $next";now!=null->"NOW  •  $now";else->a[1]}
   val action={if(ch!=null)playRecent(ch.url) else if(i<2)loadChannels("ERT") else loadChannels()}
   channelRow.addView(
    logoCard(a[0],sub,a[2].toInt(),a[3]=="1",action),
    LinearLayout.LayoutParams(0,138,1f).apply{setMargins(0,0,12,0)}
   )
  }
  main.addView(channelRow)

  sectionTitle("Recently Watched")
  val cont=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  run{
   val recent=try{JSONArray(prefs.getString("recent_channels","[]")?:"[]")}catch(_:Exception){JSONArray()}
   if(recent.length()==0){
    val cardView=imageCard("Start watching","Recently watched channels will appear here","https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"){loadChannels()}
    cont.addView(cardView,LinearLayout.LayoutParams(0,182,1f).apply{setMargins(0,0,12,0)})
   }else{
    for(i in 0 until minOf(4,recent.length())){
     val o=recent.optJSONObject(i)?:continue
     val name=o.optString("name","Channel")
     val group=o.optString("group","Recently watched")
     val url=o.optString("url","")
     val id=o.optString("tvgId","")
     val watchedAt=o.optLong("watchedAt",0L)
     val now=id.takeIf{it.isNotBlank()}?.let{epgNow[it]}
     val subtitle=if(now!=null)"NOW  •  $now" else watchedAgo(watchedAt)
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
  val categoryData=listOf(
   arrayOf("▣  Greek TV","All Greek Channels",Color.rgb(18,124,210).toString()),
   arrayOf("◉  Movies","Greek & International",Color.rgb(155,26,83).toString()),
   arrayOf("▦  TV Guide","Now & Next",Color.rgb(4,116,68).toString()),
   arrayOf("★  Kids","For the Little Ones",Color.rgb(225,124,5).toString()),
   arrayOf("◉  $placeName","Local Content",Color.rgb(6,132,153).toString()),
   arrayOf("◎  World TV","International Channels",Color.rgb(95,19,160).toString())
  )
  categoryData.forEachIndexed{i,a->
   val action=when(i){0->{ {loadChannels()} };1->{ {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")} };2->{ {showTvGuide()} };3->{ {loadChannels("ΠΑΙΔΙΚΑ")} };4->{ {loadChannels(placeFilter)} };else->{ {loadChannels("ΔΙΕΘΝΗ")} }}
   cats.addView(tvCard(a[0],a[1],a[2].toInt(),action).apply{gravity=Gravity.CENTER_VERTICAL;elevation=4f},LinearLayout.LayoutParams(0,100,1f).apply{setMargins(0,0,12,0)})
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
    liveRow.addView(tvCard("●  "+ch.name,if(ch.group.isBlank())"LIVE TV" else ch.group,Color.rgb(16,74,132)){playRecent(ch.url)},LinearLayout.LayoutParams(0,100,1f).apply{setMargins(0,0,12,0)})
   }
  }
  main.addView(liveRow)

  body.addView(main,LinearLayout.LayoutParams(0,-1,1f))
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
  loadImageInto(img,url)
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
 private fun loadChannels(filter:String?=null,favouritesOnly:Boolean=false){currentSection=when{favouritesOnly->"FAVOURITES";filter?.contains(placeFilter,true)==true->placeUpper;filter?.contains("ΠΑΙΔΙΚΑ",true)==true->"KIDS";filter?.contains("ΤΑΙΝΙΕΣ",true)==true->"ON DEMAND";filter?.contains("ΔΙΕΘΝΗ",true)==true->"WORLD TV";filter?.contains("ERT",true)==true->"ERT";else->"LIVE TV"};Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val out=parsePlaylist(txt).filter{(filter==null||it.group.contains(filter,true))&&(!favouritesOnly||fav.has(it.url))};runOnUiThread{channels=out;if(out.isEmpty()){showEmptyState(if(favouritesOnly)"FAVOURITES" else currentSection,if(favouritesOnly)"No favourites yet. Hold OK on a channel to add one." else "Nothing is available in this section right now.")}else{showList()}}}catch(e:Exception){runOnUiThread{showMessage(brandName,"Unable to load right now. Check the internet connection and try again.")}}}.start()}
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
  player!!.addListener(object:Player.Listener{
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
 private fun showEmptyState(title:String,message:String){
  screenMode="LIST"
  val root=shell(title)
  root.gravity=Gravity.CENTER_HORIZONTAL
  root.addView(TextView(this).apply{text="◌";textSize=48f;gravity=Gravity.CENTER;setTextColor(accent);setPadding(0,70,0,14)})
  root.addView(TextView(this).apply{text=message;textSize=20f;gravity=Gravity.CENTER;setTextColor(Color.rgb(202,218,232));setPadding(40,0,40,28)})
  root.addView(button("←  Back to Home"){showHome()},LinearLayout.LayoutParams(360,72))
  setContentView(root)
 }
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage(brandName,"Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
