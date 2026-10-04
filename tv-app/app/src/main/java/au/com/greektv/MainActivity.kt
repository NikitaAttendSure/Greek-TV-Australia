package au.com.greektv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.URL

data class Channel(val name:String, val url:String, val group:String)

class MainActivity : Activity() {
    private val bg=Color.rgb(10,20,34); private val card=Color.rgb(25,48,76)
    private var player:ExoPlayer?=null; private var channels=listOf<Channel>(); private var current=0

    override fun onCreate(b:Bundle?){super.onCreate(b);showHome()}
    override fun onStop(){super.onStop(); player?.release(); player=null}

    private fun button(title:String, action:()->Unit)=Button(this).apply{
        text=title;textSize=24f;isAllCaps=false;setTextColor(Color.WHITE);setBackgroundColor(card)
        isFocusable=true;setPadding(28,20,28,20);setOnClickListener{action()}
        layoutParams=LinearLayout.LayoutParams(-1,82).apply{setMargins(0,7,0,7)}
        setOnFocusChangeListener{v,f->v.alpha=if(f)1f else .78f;v.scaleX=if(f)1.025f else 1f;v.scaleY=v.scaleX}
    }
    private fun showHome(){
        player?.release();player=null
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;setPadding(64,42,64,42);setBackgroundColor(bg)}
        root.addView(TextView(this).apply{text="🇬🇷  GREEK TV";textSize=34f;setTextColor(Color.WHITE);setPadding(10,0,0,28)})
        listOf(
            "📺  LIVE TV" to { {loadChannels()} },
            "🍷  ΜΠΡΟΥΣΚΟ" to { {openBrousko()} },
            "🎬  ΤΑΙΝΙΕΣ" to { {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")} },
            "📺  ΣΕΙΡΕΣ" to { {showMessage("ΣΕΙΡΕΣ","Περισσότερες ελληνικές σειρές σύντομα")} },
            "🎵  ΜΟΥΣΙΚΗ" to { {loadChannels("ΕΛΛΗΝΙΚΗ ΜΟΥΣΙΚΗ")} },
            "🧸  ΠΑΙΔΙΚΑ" to { {loadChannels("ΠΑΙΔΙΚΑ")} }
        ).forEach{(t,a)->root.addView(button(t,a()))}
        setContentView(root);root.post{root.getChildAt(1).requestFocus()}
    }
    private fun loadChannels(filter:String?=null){
        Thread{
            try{
                val text=URL("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u").readText()
                val out=mutableListOf<Channel>();var name="";var group=""
                text.lines().forEach{line->
                    if(line.startsWith("#EXTINF")){
                        name=line.substringAfterLast(",").trim()
                        group=Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?:""
                    } else if(line.startsWith("http") && name.isNotBlank()){
                        if(filter==null || group.contains(filter,true))out.add(Channel(name,line.trim(),group));name=""
                    }
                }
                runOnUiThread{channels=out;if(out.isEmpty())showMessage("GREEK TV","Δεν βρέθηκαν κανάλια.") else showChannelList()}
            }catch(e:Exception){runOnUiThread{showMessage("GREEK TV","Δεν ήταν δυνατή η φόρτωση της λίστας.")}}
        }.start()
    }
    private fun showChannelList(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(48,32,48,32);setBackgroundColor(bg)}
        root.addView(TextView(this).apply{text="📺  ΚΑΝΑΛΙΑ";textSize=30f;setTextColor(Color.WHITE)})
        val scroll=ScrollView(this);val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        channels.forEachIndexed{i,c->list.addView(button(c.name){play(i)})};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root);list.post{if(list.childCount>0)list.getChildAt(0).requestFocus()}
    }
    private fun play(i:Int){
        current=i;player?.release();player=ExoPlayer.Builder(this).build()
        val view=PlayerView(this).apply{this.player=this@MainActivity.player;useController=true;setBackgroundColor(Color.BLACK)}
        setContentView(view);player!!.setMediaItem(MediaItem.fromUri(channels[i].url));player!!.prepare();player!!.play()
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean{
        if(player!=null && channels.isNotEmpty()){
            if(keyCode==KeyEvent.KEYCODE_DPAD_UP || keyCode==KeyEvent.KEYCODE_CHANNEL_UP){play((current+1)%channels.size);return true}
            if(keyCode==KeyEvent.KEYCODE_DPAD_DOWN || keyCode==KeyEvent.KEYCODE_CHANNEL_DOWN){play((current-1+channels.size)%channels.size);return true}
        }
        if(keyCode==KeyEvent.KEYCODE_BACK){if(player!=null){player?.release();player=null;showChannelList();return true}}
        return super.onKeyDown(keyCode,event)
    }
    private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
    private fun openUri(url:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(url));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage("GREEK TV","Δεν βρέθηκε εφαρμογή για άνοιγμα.")}
    private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
