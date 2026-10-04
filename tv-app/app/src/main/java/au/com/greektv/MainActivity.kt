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
 private var player:ExoPlayer?=null;private var previewPlayer:ExoPlayer?=null;private var channels=listOf<Channel>();private var current=0;private var overlay:TextView?=null
 private lateinit var fav:Favourites
 override fun onCreate(b:Bundle?){super.onCreate(b);fav=Favourites(this);showHome()}
 private val prefs by lazy{getSharedPreferences("greek_tv",MODE_PRIVATE)}
 override fun onStop(){super.onStop();player?.release();player=null;previewPlayer?.release();previewPlayer=null}
 private fun panel(c:Int,r:Float=22f)=GradientDrawable().apply{setColor(c);cornerRadius=r;setStroke(1,Color.argb(72,120,180,230))}
 private fun button(t:String,a:()->Unit)=Button(this).apply{text=t;textSize=21f;gravity=Gravity.CENTER_VERTICAL;isAllCaps=false;typeface=Typeface.create("sans-serif-medium",0);setTextColor(Color.WHITE);background=panel(card);isFocusable=true;setPadding(30,0,24,0);stateListAnimator=null;setOnClickListener{a()};layoutParams=LinearLayout.LayoutParams(-1,72).apply{setMargins(0,5,0,5)};setOnFocusChangeListener{v,f->background=panel(if(f)focus else card);v.animate().scaleX(if(f)1.045f else 1f).scaleY(if(f)1.045f else 1f).setDuration(120).start();v.elevation=if(f)14f else 1f}}
 private fun shell(title:String):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(64,34,64,28);setBackgroundColor(bg);addView(TextView(this@MainActivity).apply{text=title;textSize=34f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.05f;setPadding(6,0,0,2)});addView(TextView(this@MainActivity).apply{text="Η Ελλάδα στο σπίτι σας  •  CHIOS → WORLD";textSize=15f;setTextColor(accent);letterSpacing=.03f;setPadding(7,0,0,22)})}
 private fun showHome(){
  player?.release();player=null;previewPlayer?.release();previewPlayer=null
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

  val root=FrameLayout(this).apply{setBackgroundColor(bg)}
  val backdrop=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;alpha=.82f;setBackgroundColor(Color.rgb(2,8,15))}
  root.addView(backdrop,FrameLayout.LayoutParams(-1,-1))
  Thread{try{
   val bmp=URL("https://commons.wikimedia.org/wiki/Special:Redirect/file/Chios_-_Port_of_Chios_(3).jpg").openStream().use{BitmapFactory.decodeStream(it)}
   runOnUiThread{backdrop.setImageBitmap(bmp)}
  }catch(_:Exception){}}.start()
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.argb(34,1,6,12),Color.argb(112,1,8,15),Color.argb(228,1,7,13)))},FrameLayout.LayoutParams(-1,-1))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,intArrayOf(Color.argb(155,246,106,28),Color.argb(64,248,149,65),Color.TRANSPARENT,Color.TRANSPARENT))},FrameLayout.LayoutParams(-1,250))
  root.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(120,0,8,18),Color.TRANSPARENT))},FrameLayout.LayoutParams(430,-1))

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
   text="RESKAKIS TV";textSize=42f;typeface=Typeface.create("sans-serif-black",Typeface.BOLD);setTextColor(Color.WHITE);letterSpacing=.012f;setSingleLine(true);setShadowLayer(10f,0f,3f,Color.argb(110,0,0,0))
  })
  wordmark.addView(TextView(this).apply{
   text="GREEK TELEVISION  ·  CHIOS  ·  AND MORE";textSize=11f;setTextColor(Color.rgb(235,240,247));letterSpacing=.10f;setSingleLine(true)
  })
  identity.addView(wordmark)
  top.addView(identity,LinearLayout.LayoutParams(0,-2,1f))
  top.addView(TextView(this).apply{
   text="From Chios\nto the World";textSize=25f;typeface=Typeface.create("cursive",Typeface.ITALIC);setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(18,0,34,0);setShadowLayer(8f,0f,3f,Color.argb(120,0,0,0))
  })
  top.addView(TextView(this).apply{
   text=SimpleDateFormat("HH:mm   |   EEE d MMM",Locale.getDefault()).format(Date())+"   ⚙";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL or Gravity.END;setSingleLine(true)
  })
  page.addView(top,LinearLayout.LayoutParams(-1,108))

  val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}

  // Glass left rail.
  val nav=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(10,12,10,10)
   background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(232,1,13,25),Color.argb(214,4,25,43))).apply{cornerRadius=16f;setStroke(1,Color.argb(72,150,195,230))}
   elevation=8f
  }
  val navItems=listOf<Pair<String,()->Unit>>(
   "⌂   Home" to {showHome()},"▣   Live TV" to {loadChannels()},"♥   Favourites" to {loadChannels(favouritesOnly=true)},"◷   Continue" to {loadLastChannel()},
   "▤   On Demand" to {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")},"♜   Chios" to {loadChannels("ΧΙΟΣ")},"◎   World TV" to {loadChannels("ΔΙΕΘΝΗ")},
   "☷   Categories" to {loadChannels()},"⌕   Search" to {loadChannels()},"⚙   Settings" to {showMessage("Settings","RESKAKIS TV • Family Edition")}
  )
  navItems.forEachIndexed{i,it->
   nav.addView(button(it.first,it.second).apply{
    textSize=14f;setPadding(16,0,8,0);setSingleLine(true);layoutParams=LinearLayout.LayoutParams(-1,50).apply{setMargins(0,2,0,2)}
    background=if(i==0)GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(11,129,231),Color.rgb(29,151,247))).apply{cornerRadius=12f;setStroke(1,Color.argb(180,255,255,255))} else panel(Color.argb(122,6,26,44),12f)
   })
  }
  body.addView(nav,LinearLayout.LayoutParams(210,-1).apply{setMargins(0,8,16,0)})

  val main=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,0,0,0)}
  fun sectionTitle(t:String){
   main.addView(TextView(this).apply{text=t;textSize=23f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE);setPadding(0,9,0,6);setShadowLayer(6f,0f,2f,Color.argb(120,0,0,0))})
  }

  sectionTitle("Popular Greek Channels")
  val channelRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val channelData=listOf(
   arrayOf("ΕΡΤ 1","ERT 1 HD",Color.rgb(18,53,205).toString()),
   arrayOf("ERT 2","ERT 2 HD",Color.rgb(235,238,241).toString()),
   arrayOf("ANT1","ANT1 HD",Color.rgb(15,53,98).toString()),
   arrayOf("A","ALPHA HD",Color.rgb(226,30,48).toString()),
   arrayOf("ΣΚΑΪ","SKAI HD",Color.rgb(20,96,229).toString()),
   arrayOf("OPEN","OPEN HD",Color.rgb(7,18,33).toString()),
   arrayOf("MEGA","MEGA HD",Color.rgb(237,239,242).toString())
  )
  channelData.forEachIndexed{i,a->
   val action={ if(i<2)loadChannels("ERT") else loadChannels() }
   val tile=tvCard(a[0],a[1],a[2].toInt(),action).apply{
    gravity=Gravity.CENTER
    (getChildAt(0) as TextView).apply{textSize=26f;gravity=Gravity.CENTER;setTextColor(if(i==1||i==6)Color.rgb(25,44,115) else Color.WHITE)}
    (getChildAt(1) as TextView).apply{gravity=Gravity.CENTER;setTextColor(if(i==1||i==6)Color.rgb(40,55,80) else Color.rgb(230,237,245))}
   }
   channelRow.addView(tile,LinearLayout.LayoutParams(0,144,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(channelRow)

  sectionTitle("Continue Watching")
  val cont=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val contCards=listOf(
   arrayOf("ERT 1 HD","News","https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("Sasmos","Drama Series","https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("Akis' Food Tour","Cooking","https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("Chios","Documentary","https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1200&q=85")
  )
  contCards.forEachIndexed{i,a->
   val action=when(i){0->{ {loadLastChannel()} };1->{ {showMessage("Sasmos","More Greek series coming soon")} };2->{ {showMessage("Food","More Greek cooking content coming soon")} };else->{ {loadChannels("ΧΙΟΣ")} }}
   cont.addView(imageCard(a[0],a[1],a[2],action),LinearLayout.LayoutParams(0,176,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(cont)

  sectionTitle("Browse by Category")
  val cats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val categoryData=listOf(
   arrayOf("▣  Greek TV","All Greek Channels",Color.rgb(18,124,210).toString()),
   arrayOf("●  Movies","Greek & International",Color.rgb(155,26,83).toString()),
   arrayOf("☻  Series","Greek Series",Color.rgb(4,116,68).toString()),
   arrayOf("★  Kids","For the Little Ones",Color.rgb(225,124,5).toString()),
   arrayOf("♜  Chios","Local Content",Color.rgb(6,132,153).toString()),
   arrayOf("◎  World TV","International Channels",Color.rgb(95,19,160).toString())
  )
  categoryData.forEachIndexed{i,a->
   val action=when(i){0->{ {loadChannels()} };1->{ {loadChannels("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")} };2->{ {showMessage("Series","More Greek series coming soon")} };3->{ {loadChannels("ΠΑΙΔΙΚΑ")} };4->{ {loadChannels("ΧΙΟΣ")} };else->{ {loadChannels("ΔΙΕΘΝΗ")} }}
   cats.addView(tvCard(a[0],a[1],a[2].toInt(),action).apply{gravity=Gravity.CENTER_VERTICAL;elevation=4f},LinearLayout.LayoutParams(0,96,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(cats)

  sectionTitle("Chios Highlights")
  val chios=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val chiosCards=listOf(
   arrayOf("Chios Live","Local Content","https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("Chios Villages","Explore","https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("Chios Beaches","Island Life","https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("Chios Documentary","History","https://images.unsplash.com/photo-1530841377377-3ff06c0ca713?auto=format&fit=crop&w=1200&q=85"),
   arrayOf("Chios Mastiha","Tradition","https://images.unsplash.com/photo-1473093295043-cdd812d0e601?auto=format&fit=crop&w=1200&q=85")
  )
  chiosCards.forEach{a->
   chios.addView(imageCard(a[0],a[1],a[2]){loadChannels("ΧΙΟΣ")},LinearLayout.LayoutParams(0,148,1f).apply{setMargins(0,0,12,0)})
  }
  main.addView(chios)

  body.addView(main,LinearLayout.LayoutParams(0,-1,1f))
  page.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(page)
  setContentView(root)
  nav.post{if(nav.childCount>0)nav.getChildAt(0).requestFocus()}
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
  val frame=FrameLayout(this).apply{isFocusable=true;isClickable=true;background=panel(Color.rgb(8,20,34),16f);elevation=5f}
  val img=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.rgb(16,30,44))}
  frame.addView(img,FrameLayout.LayoutParams(-1,-1))
  Thread{try{val bmp=URL(url).openStream().use{BitmapFactory.decodeStream(it)};runOnUiThread{img.setImageBitmap(bmp)}}catch(_:Exception){}}.start()
  val shade=View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(Color.argb(235,2,8,14),Color.argb(96,2,8,14),Color.argb(20,2,8,14)))}
  frame.addView(shade,FrameLayout.LayoutParams(-1,-1))
  val textWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8)}
  textWrap.addView(TextView(this).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setShadowLayer(4f,0f,1f,Color.BLACK)})
  textWrap.addView(TextView(this).apply{text=subtitle;textSize=11f;setTextColor(Color.rgb(230,237,244))})
  frame.addView(textWrap,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  frame.setOnClickListener{action()}
  frame.setOnFocusChangeListener{v,f->v.foreground=if(f)GradientDrawable().apply{setColor(Color.TRANSPARENT);setStroke(4,Color.WHITE);cornerRadius=14f}else null;v.animate().scaleX(if(f)1.045f else 1f).scaleY(if(f)1.045f else 1f).setDuration(110).start();v.elevation=if(f)18f else 2f}
  return frame
 }
 private fun loadLastChannel(){val u=prefs.getString("last_channel",null);if(u==null){loadChannels();return};Thread{try{val all=parsePlaylist(fetchPlaylist());runOnUiThread{channels=all;val i=all.indexOfFirst{it.url==u};if(i>=0)play(i)else showList()}}catch(e:Exception){runOnUiThread{loadChannels()}}}.start()}
 private fun fetchPlaylist():String{val u=URL("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u");val c=u.openConnection().apply{connectTimeout=8000;readTimeout=12000};return c.getInputStream().bufferedReader().use{it.readText()}.also{prefs.edit().putString("playlist_cache",it).apply()}}
 private fun parsePlaylist(txt:String):List<Channel>{val out=mutableListOf<Channel>();var n="";var g="";txt.lines().forEach{l->if(l.startsWith("#EXTINF")){n=l.substringAfterLast(",").trim();g=l.substringAfter("group-title=\"", "").substringBefore("\"", "")}else if(l.startsWith("http")&&n.isNotBlank()){out.add(Channel(n,l.trim(),g));n=""}};return out}
 private fun loadChannels(filter:String?=null,favouritesOnly:Boolean=false){Thread{try{val txt=try{fetchPlaylist()}catch(e:Exception){prefs.getString("playlist_cache",null)?:throw e};val out=parsePlaylist(txt).filter{(filter==null||it.group.contains(filter,true))&&(!favouritesOnly||fav.has(it.url))};runOnUiThread{channels=out;if(out.isEmpty()){showMessage("RESKAKIS TV",if(favouritesOnly)"Δεν υπάρχουν αγαπημένα ακόμη." else "Δεν βρέθηκαν κανάλια.")}else{showList()}}}catch(e:Exception){runOnUiThread{showMessage("RESKAKIS TV","Δεν ήταν δυνατή η φόρτωση. Ελέγξτε το Internet και δοκιμάστε ξανά.")}}}.start()}
 private fun showList(){
  previewPlayer?.release();previewPlayer=null
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(42,28,42,28);setBackgroundColor(bg)}

  val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val titleWrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  titleWrap.addView(TextView(this).apply{text="LIVE TV";textSize=34f;typeface=Typeface.create("sans-serif",Typeface.BOLD);setTextColor(Color.WHITE)})
  titleWrap.addView(TextView(this).apply{text="RESKAKIS TV  •  LIVE GREEK TELEVISION";textSize=13f;setTextColor(accent);letterSpacing=.045f})
  header.addView(titleWrap,LinearLayout.LayoutParams(0,-2,1f))
  header.addView(TextView(this).apply{text="▲▼ Browse   •   OK Full Screen   •   ★ Favourite";textSize=14f;setTextColor(muted);gravity=Gravity.END})
  root.addView(header,LinearLayout.LayoutParams(-1,76))

  val content=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}

  val listPane=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   background=GradientDrawable().apply{setColor(Color.rgb(5,18,31));cornerRadius=18f;setStroke(1,Color.argb(70,120,180,230))}
   setPadding(12,12,12,12)
  }
  val scroll=ScrollView(this)
  val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  val previewTitle=TextView(this).apply{text="Select a channel";textSize=26f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)}
  val previewMeta=TextView(this).apply{text="Live preview";textSize=14f;setTextColor(accent);setPadding(0,5,0,10)}

  val previewPane=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(18,0,0,0)
  }
  val videoFrame=FrameLayout(this).apply{
   background=GradientDrawable().apply{setColor(Color.BLACK);cornerRadius=18f;setStroke(1,Color.argb(100,120,180,230))}
  }
  val playerView=PlayerView(this).apply{
   useController=false
   keepScreenOn=true
   setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
   setBackgroundColor(Color.BLACK)
  }
  videoFrame.addView(playerView,FrameLayout.LayoutParams(-1,-1))
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
   previewMeta.text=(if(channels[index].group.isBlank())"LIVE NOW" else channels[index].group.uppercase())+"   •   LIVE NOW"
   previewPlayer?.release()
   previewPlayer=ExoPlayer.Builder(this).build().also{p->
    playerView.player=p
    p.volume=0f
    p.setMediaItem(MediaItem.fromUri(channels[index].url))
    p.prepare()
    p.play()
   }
  }

  channels.forEachIndexed{i,ch->
   val row=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true
    setPadding(18,0,16,0)
    background=panel(Color.rgb(10,31,50),12f)
    layoutParams=LinearLayout.LayoutParams(-1,62).apply{setMargins(0,0,0,7)}
   }
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
   row.addView(badge,LinearLayout.LayoutParams(48,38).apply{setMargins(0,0,14,0)})
   val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   info.addView(TextView(this).apply{text=ch.name;textSize=18f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setTextColor(Color.WHITE);setSingleLine(true)})
   info.addView(TextView(this).apply{text=if(ch.group.isBlank())"Live TV" else ch.group;textSize=11f;setTextColor(muted);setSingleLine(true)})
   row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
   row.addView(TextView(this).apply{text=if(fav.has(ch.url))"★" else "›";textSize=20f;setTextColor(if(fav.has(ch.url))Color.rgb(255,203,62) else Color.LTGRAY);gravity=Gravity.CENTER})
   row.setOnClickListener{previewPlayer?.release();previewPlayer=null;play(i)}
   row.setOnLongClickListener{val added=fav.toggle(ch.url);Toast.makeText(this,if(added)"★ Added to favourites" else "Removed from favourites",Toast.LENGTH_SHORT).show();showList();true}
   row.setOnFocusChangeListener{v,hasFocus->
    v.background=panel(if(hasFocus)focus else Color.rgb(10,31,50),12f)
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
  list.post{if(list.childCount>0)list.getChildAt(0).requestFocus()}
 }
 private fun play(i:Int){previewPlayer?.release();previewPlayer=null;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;current=i;prefs.edit().putString("last_channel",channels[i].url).apply();player?.release();player=ExoPlayer.Builder(this).build();player!!.addListener(object:Player.Listener{override fun onPlayerError(error:PlaybackException){runOnUiThread{Toast.makeText(this@MainActivity,"Το κανάλι δεν είναι διαθέσιμο. Δοκιμάστε άλλο.",Toast.LENGTH_LONG).show();showList()}}});val frame=FrameLayout(this);val v=PlayerView(this).apply{player=this@MainActivity.player;useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);keepScreenOn=true;controllerShowTimeoutMs=3000;setBackgroundColor(Color.BLACK)};frame.addView(v,FrameLayout.LayoutParams(-1,-1));overlay=TextView(this).apply{text=channels[i].name;textSize=24f;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(Color.WHITE);background=panel(Color.argb(225,10,24,39));setPadding(28,16,28,16)};frame.addView(overlay,FrameLayout.LayoutParams(-2,-2,Gravity.START or Gravity.BOTTOM).apply{setMargins(42,0,0,42)});setContentView(frame);overlay?.postDelayed({overlay?.visibility=View.GONE},2600);player!!.setMediaItem(MediaItem.fromUri(channels[i].url));player!!.prepare();player!!.play()}
 override fun onKeyDown(k:Int,e:KeyEvent?):Boolean{if(player!=null&&channels.isNotEmpty()){when(k){KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_CHANNEL_UP->{play((current+1)%channels.size);return true};KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_CHANNEL_DOWN->{play((current-1+channels.size)%channels.size);return true};KeyEvent.KEYCODE_STAR,KeyEvent.KEYCODE_BOOKMARK->{fav.toggle(channels[current].url);Toast.makeText(this,if(fav.has(channels[current].url))"★ Προστέθηκε στα αγαπημένα" else "Αφαιρέθηκε από τα αγαπημένα",Toast.LENGTH_SHORT).show();return true};KeyEvent.KEYCODE_BACK->{player?.release();player=null;showList();return true}}};return super.onKeyDown(k,e)}
 private fun openBrousko(){openUri("https://www.antenna.gr/mprousko")}
 private fun openUri(u:String){val i=Intent(Intent.ACTION_VIEW,Uri.parse(u));if(i.resolveActivity(packageManager)!=null)startActivity(i)else showMessage("RESKAKIS TV","Δεν βρέθηκε συμβατή εφαρμογή.")}
 private fun showMessage(t:String,m:String){AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK",null).show()}
}
