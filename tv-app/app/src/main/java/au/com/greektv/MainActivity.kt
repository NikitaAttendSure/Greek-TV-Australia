package au.com.greektv

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class Channel(val name:String,val url:String,val group:String)
class MainActivity:Activity(){
 private val bg=Color.rgb(2,7,13);private val card=Color.rgb(12,25,40);private val focus=Color.rgb(27,105,190);private val muted=Color.rgb(158,180,201);private val accent=Color.rgb(64,151,255)
 private var player:ExoPlayer?=null;private var channels=listOf<Channel>();private var current=0;private var overlay:TextView?=null
 private lateinit var fav:Favourites
 override fun onCreate(b:Bundle?){super.onCreate(b);fav=Favourites(this);showHome()}
 private val prefs by lazy{getSharedPreferences("greek_tv",MODE_PRIVATE)}
 override fun onStop(){super.onStop();player?.release();player=null}
 private fun panel(c:Int,r:Float=22f)=GradientDrawable().apply{setColor(c);cornerRadius=r;setStroke(1,Color.argb(72,120,180,230))}
 private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=21f;gravity=Gravity.CENTER_VERTICAL;isAllCaps=false;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.WHITE);background=panel(card);isFocusable=true;setPadding(30,0,24,0);stateListAnimator=null;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,72).apply{setMargins(0,5,0,5)};setOnFocusChangeListener{v,f->background=panel(if(f)focus else card);v.animate().scaleX(if(f)1.045f else 1f).scaleY(if(f)1.045f else 1f).setDuration(120).start();v.elevation=if(f)14f else 1f}}
 private fun shell(title:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(64,34,64,28);setBackgroundColor(bg);addView(TextView(this@MainActivity).apply{text=title;textSize=34f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.05f;setPadding(6,0,0,2)});addView(TextView(this@MainActivity).apply{text="Η Ελλάδα στο σπίτι σας  •  CHIOS → WORLD";textSize=15f;setTextColor(accent);letterSpacing=.03f;setPadding(7,0,0,22)})}
 private fun showHome(){
  player?.release();player=null
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  val root=FrameLayout(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(7,39,70),Color.rgb(5,20,36),Color.rgb(1,7,13)))}
  val backdrop=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;alpha=.46f;setBackgroundColor(Color.rgb(2,8,16))}
  root.addView(backdrop,FrameLayout.LayoutParams(-1,-1))
  Thread{
   try{
    val bmp=URL("https://commons.wikimedia.org/wiki/Special:Redirect/file/Chios_-_Port_of_Chios_(3).jpg").openStream().use{BitmapFactory.decodeStream(it)}
    runOnUiThread{backdrop.setImageBitmap(bmp)}
   }catch(_:Exception){}
  }.start()
  val shade=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(225,1,7,13),Color.argb(145,2,10,18),Color.argb(205,1,6,11)))}
  root.addView(shade,FrameLayout.LayoutParams(-1,-1))
  val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(12,14,16,12)}
  val nav=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,12,12);background=GradientDrawable().apply{setColor(Color.argb(218,2,14,25));cornerRadius=16f;setStroke(1,Color.argb(70,130,190,235))}}
  nav.addView(TextView(this).apply{text="🇬🇷  RESKAKIS TV";textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);letterSpacing=.035f;setPadding(8,0,0,2)})
  nav.addView(TextView(this).apply{text="GREEK TELEVISION · CHIOS";textSize=9f;setTextColor(muted);letterSpacing=.08f;setPadding(10,0,0,9)})
  val navItems=listOf<Pair<String,()->Unit>>("⌂   Home" to {showHome()},"▣   Live TV" to {loadChannels()},"♥   Favourites" to {loadChannels(favouritesOnly=true)},"◷   Continue" to {loadLastChannel()},"▤   On Demand" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},"♜   Chios" to {loadChannels("ΧΙΟΣ")},"◎   World TV" to {loadChannels("ΔΙΕΘΝΗ")},"☷   Categories" to {loadChannels()},"⌕   Search" to {loadChannels()},"⚙   Settings" to {showMessage("Settings","RESKAKIS TV • Family Edition")})
  navItems.forEachIndexed{i,it->nav.addView(button(it.first,it.second).apply{textSize=15f;setPadding(16,0,10,0);layoutParams=LinearLayout.LayoutParams(-1,52).apply{setMargins(0,2,0,2)};if(i==0)background=panel(focus,16f)})}
  body.addView(nav,LinearLayout.LayoutParams(285,-1))
  val main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,0,8,0)}
  val mast=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val brand=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  brand.addView(TextView(this).apply{text="RESKAKIS TV";textSize=34f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);letterSpacing=.035f})
  brand.addView(TextView(this).apply{text="GREEK TELEVISION  ·  CHIOS  ·  AND MORE";textSize=10f;setTextColor(Color.WHITE)})
  mast.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
  mast.addView(TextView(this).apply{text="From Chios to the World";textSize=18f;typeface=Typeface.create("cursive",Typeface.ITALIC);setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL;setPadding(8,0,18,0)})
  mast.addView(TextView(this).apply{text=SimpleDateFormat("HH:mm  |  EEE d MMM",Locale.getDefault()).format(Date());textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.END})
  main.addView(mast,LinearLayout.LayoutParams(-1,82))
  fun section(title:String,items:List<Pair<String,()->Unit>>,height:Int=92,tones:IntArray=intArrayOf(Color.rgb(18,68,122),Color.rgb(18,43,75))){
   main.addView(TextView(this).apply{text=title;textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setPadding(0,2,0,3)})
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   items.forEachIndexed{idx,item->
    val b=button(item.first,item.second).apply{textSize=14f;gravity=Gravity.CENTER;setPadding(6,0,6,0);val base=tones[idx%tones.size];background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,Color.rgb(8,22,37))).apply{cornerRadius=10f;setStroke(1,Color.argb(90,120,180,230))};setOnFocusChangeListener{v,f->background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(if(f)focus else base,Color.rgb(7,24,42))).apply{cornerRadius=10f;setStroke(if(f)3 else 1,if(f)Color.WHITE else Color.argb(100,120,180,230))};v.animate().scaleX(if(f)1.055f else 1f).scaleY(if(f)1.055f else 1f).setDuration(110).start();v.elevation=if(f)18f else 2f}}
    row.addView(b,LinearLayout.LayoutParams(0,height,1f).apply{setMargins(0,2,10,5)})
   };main.addView(row)
  }
  section("Popular Greek Channels",listOf("ERT 1 HD" to {loadChannels("ERT")},"ERT 2 HD" to {loadChannels("ERT")},"ANT1 HD" to {loadChannels()},"ALPHA HD" to {loadChannels()},"SKAI HD" to {loadChannels()},"OPEN HD" to {loadChannels()},"MEGA HD" to {loadChannels()}),84,intArrayOf(Color.rgb(14,50,190),Color.rgb(225,231,234),Color.rgb(13,55,99),Color.rgb(216,33,50),Color.rgb(16,94,224),Color.rgb(10,21,36),Color.rgb(225,226,229)))
  section("Continue Watching",listOf("▶ ERT 1 HD  ·  News" to {loadLastChannel()},"♥ Favourites" to {loadChannels(favouritesOnly=true)},"▶ Greek Series" to {showMessage("Series","More Greek series coming soon")},"▶ Chios Documentary" to {loadChannels("ΧΙΟΣ")}),68)
  section("Browse by Category",listOf("▣ Greek TV" to {loadChannels()},"● Movies" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},"☻ Series" to {showMessage("Series","More Greek series coming soon")},"★ Kids" to {loadChannels("ΠΑΙΔΙΚΑ")},"♜ Chios" to {loadChannels("ΧΙΟΣ")},"◎ World TV" to {loadChannels("ΔΙΕΘΝΗ")}),62,intArrayOf(Color.rgb(17,112,196),Color.rgb(146,25,76),Color.rgb(5,111,66),Color.rgb(222,119,3),Color.rgb(4,125,145),Color.rgb(94,17,155)))
  section("Chios Highlights",listOf("Chios Live" to {loadChannels("ΧΙΟΣ")},"Chios Villages" to {loadChannels("ΧΙΟΣ")},"Chios Beaches" to {loadChannels("ΧΙΟΣ")},"Chios Documentary" to {loadChannels("ΧΙΟΣ")},"Chios Mastiha" to {loadChannels("ΧΙΟΣ")}),64,intArrayOf(Color.rgb(21,86,126),Color.rgb(93,65,42),Color.rgb(15,111,143),Color.rgb(46,78,107),Color.rgb(132,94,48)))
  body.addView(main,LinearLayout.LayoutParams(0,-1,1f));root.addView(body)
  root.addView(TextView(this).apply{text="⚙";textSize=24f;setTextColor(Color.WHITE);setPadding(0,0,22,0);gravity=Gravity.CENTER;setOnClickListener{showMessage("Settings","RESKAKIS TV • Family Edition")}},FrameLayout.LayoutParams(60,60,Gravity.TOP or Gravity.END))
  setContentView(root)
  nav.post{if(nav.childCount>2)nav.getChildAt(2).requestFocus()}
 }
 private fun loadLastChannel(){val u=prefs.getString("last_channel",null);if(u==null){loadChannels();return};Thread{try{val all=parsePlaylist(fetchPlaylist());runOnUiThread{channels=all;val i=all.indexOfFirst{it.url==u};if(i>=0)play(i)else showList()}}catch(e:Exception){runOnUiThread{loadChannels()}}}.start()}
 private fun fetchPlaylist():String{val u=URL("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u");val c=u.openConnection().apply{connectTimeout=8000;readTimeout=12000};return c.getInputStream().bufferedReader().use{it.readText()}.also{prefs.edit().putString("playlist_cache",it).apply()}}
 private fun parsePlaylist(txt:String):List<Channel>{val out=mutableListOf<Channel>();var n="";var g="";txt.lines().forEach{l->if(l.startsWith("#EXTINF")){n=l.substringAfterLast(",").trim();g=l.substringAfter("group-title=\"", "").substringBefore("\"", "")}else if(l.startsWith("http")&&n.isNotBlank()){out.add(Channel(n,l.trim(),g));n=""}};return out}
 private fun loadChannels(filter:String?=null,favouritesOnly:Boolean=false){Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val out=parsePlaylist(txt).filter{(filter==null||it.group.contains(filter,true))&&(!favouritesOnly||fav.has(it.url))};runOnUiThread{channels=out;if(out.isEmpty()){showMessage("RESKAKIS TV",if(favouritesOnly)"Δεν υπάρχουν αγαπημένα ακόμη." else "Δεν βρέθηκαν κανάλια.")}else{showList()}}}catch(e:Exception){runOnUiThread{showMessage("RESKAKIS TV","Δεν ήταν δυνατή η φόρτωση. Ελέγξτε το Internet και δοκιμάστε ξανά.")}}}.start()}
 private fun showList(){val r=shell("ΚΑΝΑΛΙΑ");r.addView(TextView(this).apply{text="OK = προβολή   •   Κρατήστε OK = αγαπημένο";textSize=17f;setTextColor(Color.LTGRAY);setPadding(6,0,0,12)});val s=ScrollView(this);val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};channels.forEachIndexed{i,c->val b=button((if(fav.has(c.url))"★  " else "")+c.name){play(i)};b.setOnLongClickListener{val added=fav.toggle(c.url);Toast.makeText(this,if(added)"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();showList();true};l.addView(b)};s.addView(l);r.addView(s,LinearLayout.LayoutParams(-1,0,1f));setContentView(r);l.post{if(l.childCount>0)l.getChildAt(0).requestFocus()}}
 private fun play(i:Int){window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;current=i;prefs.edit().putString("last_channel",channels[i].url).apply();player?.release();player=ExoPlayer.Builder(this).build();player!!.addListener(object:Player.Listener{override fun onPlayerError(error:PlaybackException){runOnUiThread{Toast.makeText(this@MainActivity,"Το κανάλι δεν είναι διαθέσιμο. Δοκιμάστε άλλο.",Toast.LENGTH_LONG).show();showList()}}});val frame=FrameLayout(this);val v=PlayerView(this).apply{player=this@MainActivity.player;useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);keepScreenOn=true;controllerShowTimeoutMs=3000;setBackgroundColor(Color.BLACK)};frame.addView(v,FrameLayout.LayoutParams(-1,-1));overlay=TextView(this).apply{text=channels[i].name;textSize=24f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);background=panel(Color.argb(225,10,24,39));setPadding(28,16,28,16)};frame.addView(overlay,FrameLayout.LayoutParams(-2,-2,Gravity.START or Gravity.BOTTOM).apply{setMargins(42,0,0,42)});setContentView(frame);overlay?.postDelayed({overlay?.visibility=View.GONE},2600);player!!.setMediaItem(MediaItem.fromUri(channels[i].url));player!!.prepare();player!!.play()}
 override fun onKeyDown(k:Int,e:KeyEvent?):Boolean{if(player!=null&&channels.isNotEmpty()){when(k){KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_CHANNEL_UP->{play((current+1)%channels.size);return true};KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_CHANNEL_DOWN->{play((current-1+channels.size)%channels.size);return true};KeyEvent.KEYCODE_STAR,KeyEvent.KEYCODE_BOOKMARK->{fav.toggle(channels[current].url);Toast.makeText(this,if(fav.has(channels[current].url))"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();return true};KeyEvent.KEYCODE_BACK->{player?.release();player=null;showList();return true}}};return super.onKeyDown(k,e)}
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage("RESKAKIS TV","Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
