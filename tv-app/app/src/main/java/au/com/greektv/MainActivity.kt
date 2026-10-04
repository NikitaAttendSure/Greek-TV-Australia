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
 private val bg=Color.rgb(4,11,20);private val card=Color.rgb(16,31,48);private val focus=Color.rgb(25,83,132);private val muted=Color.rgb(164,180,196)
 private var player:ExoPlayer?=null;private var channels=listOf<Channel>();private var current=0
 private lateinit var fav:Favourites
 override fun onCreate(b:Bundle?){super.onCreate(b);fav=Favourites(this);showHome()}
 private val prefs by lazy{getSharedPreferences("greek_tv",MODE_PRIVATE)}
 override fun onStop(){super.onStop();player?.release();player=null}
 private fun panel(c:Int)=GradientDrawable().apply{setColor(c);cornerRadius=18f}
 private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=22f;gravity=Gravity.CENTER_VERTICAL;isAllCaps=false;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.WHITE);background=panel(card);isFocusable=true;setPadding(28,0,24,0);setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,76).apply{setMargins(0,6,0,6)};setOnFocusChangeListener{v,f->background=panel(if(f)focus else card);v.scaleX=if(f)1.018f else 1f;v.scaleY=v.scaleX}}
 private fun shell(title:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(58,34,58,30);setBackgroundColor(bg);addView(TextView(this@MainActivity).apply{text=title;textSize=30f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.04f;setPadding(6,0,0,4)});addView(TextView(this@MainActivity).apply{text="Η Ελλάδα στο σπίτι σας";textSize=16f;setTextColor(muted);setPadding(7,0,0,20)})}
 private fun showHome(){player?.release();player=null;val r=shell("RESKAKIS  TV");listOf<Pair<String,()->Unit>>(
  "▶   ΣΥΝΕΧΙΣΤΕ" to {loadLastChannel()},"📺   LIVE TV" to {loadChannels()},"★   ΑΓΑΠΗΜΕΝΑ" to {loadChannels(favouritesOnly=true)},
  "Μ   ΜΠΡΟΥΣΚΟ" to {openBrousko()},"●   ΤΑΙΝΙΕΣ" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},
  "▣   ΣΕΙΡΕΣ" to {showMessage("ΣΕΙΡΕΣ","Περισσότερες ελληνικές σειρές σύντομα")},
  "♪   ΜΟΥΣΙΚΗ" to {loadChannels("ΕΛΛΗΝΙΚΗ ΜΟΥΣΙΚΗ")},"◆   ΠΑΙΔΙΚΑ" to {loadChannels("ΠΑΙΔΙΚΑ")}
 ).forEach{r.addView(button(it.first,it.second))};setContentView(r);r.post{r.getChildAt(1).requestFocus()}}
 private fun loadLastChannel(){val u=prefs.getString("last_channel",null);if(u==null){loadChannels();return};Thread{try{val all=parsePlaylist(fetchPlaylist());runOnUiThread{channels=all;val i=all.indexOfFirst{it.url==u};if(i>=0)play(i)else showList()}}catch(e:Exception){runOnUiThread{loadChannels()}}}.start()}
 private fun fetchPlaylist():String{val u=URL("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u");val c=u.openConnection().apply{connectTimeout=8000;readTimeout=12000};return c.getInputStream().bufferedReader().use{it.readText()}.also{prefs.edit().putString("playlist_cache",it).apply()}}
 private fun parsePlaylist(txt:String):List<Channel>{val out=mutableListOf<Channel>();var n="";var g="";txt.lines().forEach{l->if(l.startsWith("#EXTINF")){n=l.substringAfterLast(",").trim();g=l.substringAfter("group-title=\"", "").substringBefore("\"", "")}else if(l.startsWith("http")&&n.isNotBlank()){out.add(Channel(n,l.trim(),g));n=""}};return out}
 private fun loadChannels(filter:String?=null,favouritesOnly:Boolean=false){Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val out=parsePlaylist(txt).filter{(filter==null||it.group.contains(filter,true))&&(!favouritesOnly||fav.has(it.url))};runOnUiThread{channels=out;if(out.isEmpty())showMessage("GREEK TV",if(favouritesOnly)"Δεν υπάρχουν αγαπημένα ακόμη.":"Δεν βρέθηκαν κανάλια.")else showList()}}catch(e:Exception){runOnUiThread{showMessage("GREEK TV","Δεν ήταν δυνατή η φόρτωση. Ελέγξτε το Internet και δοκιμάστε ξανά.")}}}.start()}
 private fun showList(){val r=shell("ΚΑΝΑΛΙΑ");r.addView(TextView(this).apply{text="OK = προβολή   •   Κρατήστε OK = αγαπημένο";textSize=17f;setTextColor(Color.LTGRAY);setPadding(6,0,0,12)});val s=ScrollView(this);val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};channels.forEachIndexed{i,c->val b=button((if(fav.has(c.url))"★  " else "")+c.name){play(i)};b.setOnLongClickListener{val added=fav.toggle(c.url);Toast.makeText(this,if(added)"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();showList();true};l.addView(b)};s.addView(l);r.addView(s,LinearLayout.LayoutParams(-1,0,1f));setContentView(r);l.post{if(l.childCount>0)l.getChildAt(0).requestFocus()}}
 private fun play(i:Int){current=i;prefs.edit().putString("last_channel",channels[i].url).apply();player?.release();player=ExoPlayer.Builder(this).build();player!!.addListener(object:Player.Listener{override fun onPlayerError(error:PlaybackException){runOnUiThread{Toast.makeText(this@MainActivity,"Το κανάλι δεν είναι διαθέσιμο. Δοκιμάστε άλλο.",Toast.LENGTH_LONG).show();showList()}}});val v=PlayerView(this).apply{player=this@MainActivity.player;useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);keepScreenOn=true;controllerShowTimeoutMs=3000;setBackgroundColor(Color.BLACK)};setContentView(v);player!!.setMediaItem(MediaItem.fromUri(channels[i].url));player!!.prepare();player!!.play()}
 override fun onKeyDown(k:Int,e:KeyEvent?):Boolean{if(player!=null&&channels.isNotEmpty()){when(k){KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_CHANNEL_UP->{play((current+1)%channels.size);return true};KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_CHANNEL_DOWN->{play((current-1+channels.size)%channels.size);return true};KeyEvent.KEYCODE_STAR,KeyEvent.KEYCODE_BOOKMARK->{fav.toggle(channels[current].url);Toast.makeText(this,if(fav.has(channels[current].url))"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();return true};KeyEvent.KEYCODE_BACK->{player?.release();player=null;showList();return true}}};return super.onKeyDown(k,e)}
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage("GREEK TV","Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
