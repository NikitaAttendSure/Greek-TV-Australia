package au.com.greektv

import android.app.*
import android.content.*
import android.graphics.Color
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
  val root=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setBackgroundColor(bg)}
  val nav=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,28,16,24);background=panel(Color.rgb(5,18,31))}
  nav.addView(TextView(this).apply{text="🇬🇷  RESKAKIS TV";textSize=23f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setPadding(10,0,0,18)})
  listOf<Pair<String,()->Unit>>(
   "⌂   Αρχική" to {showHome()},"▣   Live TV" to {loadChannels()},"♥   Αγαπημένα" to {loadChannels(favouritesOnly=true)},
   "◷   Συνέχεια" to {loadLastChannel()},"▶   Ταινίες" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},
   "♜   Χίος" to {loadChannels("ΧΙΟΣ")},"◎   Κόσμος" to {loadChannels("ΔΙΕΘΝΗ")},"☷   Κατηγορίες" to {loadChannels()},
   "⌕   Αναζήτηση" to {loadChannels()},"⚙   Ρυθμίσεις" to {showMessage("Ρυθμίσεις","RESKAKIS TV • Family Edition")}
  ).forEach{nav.addView(button(it.first,it.second))}
  root.addView(nav,LinearLayout.LayoutParams(250,-1))
  val main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,28,40,26)}
  val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,20,28,22);background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(8,35,61),Color.rgb(4,18,32),Color.rgb(2,8,15))).apply{cornerRadius=28f;setStroke(1,Color.argb(80,90,165,225))}}
  hero.addView(TextView(this).apply{text="RESKAKIS TV";textSize=40f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);letterSpacing=.035f})
  hero.addView(TextView(this).apply{text="GREEK TELEVISION  ·  CHIOS  ·  AND MORE";textSize=14f;setTextColor(accent);letterSpacing=.08f;setPadding(1,2,0,5)})
  hero.addView(TextView(this).apply{text="From Chios to the World";textSize=25f;typeface=Typeface.create("sans-serif-light",Typeface.ITALIC);setTextColor(Color.WHITE)})
  hero.addView(TextView(this).apply{text="Live Greek television, favourites and family viewing — all in one place.";textSize=15f;setTextColor(muted);setPadding(1,5,0,0)})
  main.addView(hero,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,12)})
  fun section(title:String,items:List<Pair<String,()->Unit>>){
   main.addView(TextView(this).apply{text=title;textSize=24f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setPadding(0,8,0,5)})
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   items.forEach{item->row.addView(button(item.first,item.second),LinearLayout.LayoutParams(0,76,1f).apply{setMargins(0,4,12,6)})}
   main.addView(row)
  }
  section("Popular Greek Channels",listOf("ΕΡΤ 1" to {loadChannels("ERT")},"ΕΡΤ 2" to {loadChannels("ERT")},"ANT1" to {loadChannels()},"ALPHA" to {loadChannels()},"ΣΚΑΪ" to {loadChannels()},"OPEN" to {loadChannels()},"MEGA" to {loadChannels()}))
  section("Continue Watching",listOf("▶  Τελευταίο κανάλι" to {loadLastChannel()},"★  Αγαπημένα" to {loadChannels(favouritesOnly=true)},"Μ  Μπρούσκο" to {openBrousko()}))
  section("Browse by Category",listOf("▣  Greek TV" to {loadChannels()},"●  Movies" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},"▦  Series" to {showMessage("ΣΕΙΡΕΣ","Περισσότερες ελληνικές σειρές σύντομα")},"◆  Kids" to {loadChannels("ΠΑΙΔΙΚΑ")},"♜  Chios" to {loadChannels("ΧΙΟΣ")},"◎  World TV" to {loadChannels("ΔΙΕΘΝΗ")}))
  main.addView(TextView(this).apply{text="FROM CHIOS TO THE WORLD";textSize=13f;setTextColor(muted);letterSpacing=.12f;setPadding(0,9,0,0)})
  section("Chios Highlights",listOf("Χίος Live" to {loadChannels("ΧΙΟΣ")},"Χιώτικα κανάλια" to {loadChannels("ΧΙΟΣ")},"Τοπική TV" to {loadChannels("ΧΙΟΣ")}))
  root.addView(main,LinearLayout.LayoutParams(0,-1,1f));setContentView(root)
  nav.post{if(nav.childCount>1)nav.getChildAt(1).requestFocus()}
 }
 private fun loadLastChannel(){val u=prefs.getString("last_channel",null);if(u==null){loadChannels();return};Thread{try{val all=parsePlaylist(fetchPlaylist());runOnUiThread{channels=all;val i=all.indexOfFirst{it.url==u};if(i>=0)play(i)else showList()}}catch(e:Exception){runOnUiThread{loadChannels()}}}.start()}
 private fun fetchPlaylist():String{val u=URL("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u");val c=u.openConnection().apply{connectTimeout=8000;readTimeout=12000};return c.getInputStream().bufferedReader().use{it.readText()}.also{prefs.edit().putString("playlist_cache",it).apply()}}
 private fun parsePlaylist(txt:String):List<Channel>{val out=mutableListOf<Channel>();var n="";var g="";txt.lines().forEach{l->if(l.startsWith("#EXTINF")){n=l.substringAfterLast(",").trim();g=l.substringAfter("group-title=\"", "").substringBefore("\"", "")}else if(l.startsWith("http")&&n.isNotBlank()){out.add(Channel(n,l.trim(),g));n=""}};return out}
 private fun loadChannels(filter:String?=null,favouritesOnly:Boolean=false){Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val out=parsePlaylist(txt).filter{(filter==null||it.group.contains(filter,true))&&(!favouritesOnly||fav.has(it.url))};runOnUiThread{channels=out;if(out.isEmpty()){showMessage("RESKAKIS TV",if(favouritesOnly)"Δεν υπάρχουν αγαπημένα ακόμη." else "Δεν βρέθηκαν κανάλια.")}else{showList()}}}catch(e:Exception){runOnUiThread{showMessage("RESKAKIS TV","Δεν ήταν δυνατή η φόρτωση. Ελέγξτε το Internet και δοκιμάστε ξανά.")}}}.start()}
 private fun showList(){val r=shell("ΚΑΝΑΛΙΑ");r.addView(TextView(this).apply{text="OK = προβολή   •   Κρατήστε OK = αγαπημένο";textSize=17f;setTextColor(Color.LTGRAY);setPadding(6,0,0,12)});val s=ScrollView(this);val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};channels.forEachIndexed{i,c->val b=button((if(fav.has(c.url))"★  " else "")+c.name){play(i)};b.setOnLongClickListener{val added=fav.toggle(c.url);Toast.makeText(this,if(added)"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();showList();true};l.addView(b)};s.addView(l);r.addView(s,LinearLayout.LayoutParams(-1,0,1f));setContentView(r);l.post{if(l.childCount>0)l.getChildAt(0).requestFocus()}}
 private fun play(i:Int){current=i;prefs.edit().putString("last_channel",channels[i].url).apply();player?.release();player=ExoPlayer.Builder(this).build();player!!.addListener(object:Player.Listener{override fun onPlayerError(error:PlaybackException){runOnUiThread{Toast.makeText(this@MainActivity,"Το κανάλι δεν είναι διαθέσιμο. Δοκιμάστε άλλο.",Toast.LENGTH_LONG).show();showList()}}});val frame=FrameLayout(this);val v=PlayerView(this).apply{player=this@MainActivity.player;useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);keepScreenOn=true;controllerShowTimeoutMs=3000;setBackgroundColor(Color.BLACK)};frame.addView(v,FrameLayout.LayoutParams(-1,-1));overlay=TextView(this).apply{text=channels[i].name;textSize=24f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);background=panel(Color.argb(225,10,24,39));setPadding(28,16,28,16)};frame.addView(overlay,FrameLayout.LayoutParams(-2,-2,Gravity.START or Gravity.BOTTOM).apply{setMargins(42,0,0,42)});setContentView(frame);overlay?.postDelayed({overlay?.visibility=View.GONE},2600);player!!.setMediaItem(MediaItem.fromUri(channels[i].url));player!!.prepare();player!!.play()}
 override fun onKeyDown(k:Int,e:KeyEvent?):Boolean{if(player!=null&&channels.isNotEmpty()){when(k){KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_CHANNEL_UP->{play((current+1)%channels.size);return true};KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_CHANNEL_DOWN->{play((current-1+channels.size)%channels.size);return true};KeyEvent.KEYCODE_STAR,KeyEvent.KEYCODE_BOOKMARK->{fav.toggle(channels[current].url);Toast.makeText(this,if(fav.has(channels[current].url))"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();return true};KeyEvent.KEYCODE_BACK->{player?.release();player=null;showList();return true}}};return super.onKeyDown(k,e)}
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage("RESKAKIS TV","Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
