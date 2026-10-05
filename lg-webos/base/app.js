const B=window.TV_BRAND;
const PLAYLIST="https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u";
let channels=[],filtered=[],current=-1,previewTimer=null,epgNow={},epgNext={};
const $=id=>document.getElementById(id);
function esc(s){return String(s||"").replace(/[&<>"]/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;"}[m]))}
function clock(){const d=new Date();$("clock").textContent=d.toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"})+"  |  "+d.toLocaleDateString([],{weekday:"short",day:"numeric",month:"short"})}
function watchedAgo(ts){if(!ts)return "Recently watched";const m=Math.max(0,Math.floor((Date.now()-ts)/60000));if(m<1)return "Watched just now";if(m<60)return "Watched "+m+" min ago";if(m<1440)return "Watched "+Math.floor(m/60)+" hr ago";return "Watched "+Math.floor(m/1440)+" d ago"}
function xmlDate(v){const m=String(v||"").match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})\s*([+-])(\d{2})(\d{2})/);if(!m)return 0;const utc=Date.UTC(+m[1],+m[2]-1,+m[3],+m[4],+m[5],+m[6]);const off=(+m[8]*60 + +m[9])*60000;return m[7]==="+"?utc-off:utc+off}
async function loadEpg(){try{const r=await fetch("https://iptv-org.github.io/epg/guides/gr/cosmote.gr.epg.xml",{cache:"no-store"});const txt=await r.text();const doc=new DOMParser().parseFromString(txt,"text/xml"),now=Date.now();epgNow={};epgNext={};Array.from(doc.getElementsByTagName("programme")).forEach(p=>{const id=p.getAttribute("channel")||"",s=xmlDate(p.getAttribute("start")),e=xmlDate(p.getAttribute("stop")),t=(p.getElementsByTagName("title")[0]||{}).textContent||"";if(!t)return;if(s<=now&&e>now)epgNow[id]=t;else if(s>now&&!epgNext[id])epgNext[id]=t});if(document.querySelector("#main")&&!document.querySelector("#playerScreen:not(.hidden)"))home()}catch(e){}}
function initBrand(){$("brandName").textContent=B.name;$("sub").textContent="GREEK TELEVISION · "+B.place.toUpperCase()+" · AND MORE";$("tagline").textContent="From "+B.place+" to the World";const heroes=[B.hero,"https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=1600&q=82","https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1600&q=82"];let hi=0;$("hero").style.backgroundImage='url("'+heroes[0]+'")';setInterval(()=>{hi=(hi+1)%heroes.length;$("hero").style.opacity=".35";setTimeout(()=>{$("hero").style.backgroundImage='url("'+heroes[hi]+'")';$("hero").style.opacity=".72"},220)},18000);clock();setInterval(clock,30000)}
function parseM3U(txt){let out=[],meta=null;txt.split(/\r?\n/).forEach(l=>{if(l.startsWith("#EXTINF")){const name=l.split(",").pop().trim();const g=(l.match(/group-title="([^"]*)"/)||[])[1]||"";const id=(l.match(/tvg-id="([^"]*)"/)||[])[1]||"";meta={name,group:g,tvgId:id}}else if(/^https?:/.test(l)&&meta){out.push({...meta,url:l.trim()});meta=null}});return out}
async function loadPlaylist(){try{const r=await fetch(PLAYLIST,{cache:"no-store"});channels=parseM3U(await r.text());localStorage.setItem("playlist",JSON.stringify(channels))}catch(e){try{channels=JSON.parse(localStorage.getItem("playlist")||"[]")}catch(_){channels=[]}}}
function navButton(label,fn){const b=document.createElement("button");b.className="navBtn";b.textContent=label;b.onclick=fn;return b}
function buildNav(){const n=$("nav");n.innerHTML="";[
["⌂   Home",home],["▣   Live TV",()=>list()],["♥   Favourites",()=>list(null,true)],["◷   Continue",continueWatch],
["▤   On Demand",()=>list("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")],["◉   "+B.place,()=>list(B.localFilter)],["◎   World TV",()=>list("ΔΙΕΘΝΗ")],
["☷   Categories",home],["⌕   Search",()=>list()],["⚙   Settings",settings]
].forEach(x=>n.appendChild(navButton(x[0],x[1])))}
function section(title,items){return '<div class="sectionTitle">'+esc(title)+'</div><div class="row">'+items.join("")+"</div>"}
function tile(title,sub,action,cls="",style=""){return '<button class="tile '+cls+'" style="'+style+'" data-action="'+action+'"><strong>'+esc(title)+'</strong><small>'+esc(sub)+'</small></button>'}
function home(){stopPreview();const r=recent();const continueTiles=r.length?r.slice(0,4).map((x,i)=>tile(x.name,(x.tvgId&&epgNow[x.tvgId]?"NOW  •  "+epgNow[x.tvgId]:watchedAgo(x.watchedAt)),"recent:"+i,"photo",'background-image:url("https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=1000&q=80")')):[
tile("ERT 1 HD","News","live","photo",'background-image:url("https://images.unsplash.com/photo-1504711434969-e33886168f5c?auto=format&fit=crop&w=1000&q=80")'),
tile("Sasmos","Drama Series","live","photo",'background-image:url("https://images.unsplash.com/photo-1485846234645-a62644f84728?auto=format&fit=crop&w=1000&q=80")'),
tile("Akis’ Food Tour","Cooking","live","photo",'background-image:url("https://images.unsplash.com/photo-1498837167922-ddd27525d352?auto=format&fit=crop&w=1000&q=80")'),
tile(B.place,"Documentary","local","photo",'background-image:url("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1000&q=80")')
];
$("main").innerHTML=
section("Popular Greek Channels",popularTiles())+
section("Recently Watched",continueTiles)+
section("Browse by Category",[
tile("▣  Greek TV","All Greek Channels","live","category",'background:linear-gradient(135deg,#1880d7,#0a57b0)'),
tile("●  Movies","Greek & International","movies","category",'background:linear-gradient(135deg,#d72a95,#8b1e72)'),
tile("▦  TV Guide","Now & Next","guide","category",'background:linear-gradient(135deg,#1f9f76,#0e6852)'),
tile("★  Kids","For the Little Ones","kids","category",'background:linear-gradient(135deg,#ef9c20,#c7680b)'),
tile("⌂  "+B.place,"Local Content","local","category",'background:linear-gradient(135deg,#2497d9,#0e6ba8)'),
tile("◎  World TV","International","world","category",'background:linear-gradient(135deg,#7d2bc9,#4e1691)')
])+
section("Live Channels",homeLiveTiles());
bindActions();focusFirst()}
function bindActions(){$("main").querySelectorAll("[data-action]").forEach(b=>b.onclick=()=>{const a=b.dataset.action;if(a==="live")list();else if(a==="guide")guide();else if(a.startsWith("popular:")){const defs=["ERT1","ERT 2","ANT1","ALPHA","SKAI","OPEN","MEGA"],key=defs[+a.split(":")[1]],ch=channels.find(x=>x.name.toUpperCase().replace("ΕΡΤ","ERT").replace("ΣΚΑΪ","SKAI").includes(key.toUpperCase()));if(ch)play(ch);else list()}else if(a==="movies")list("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ");else if(a==="kids")list("ΠΑΙΔΙΚΑ");else if(a==="local")list(B.localFilter);else if(a==="world")list("ΔΙΕΘΝΗ");else if(a.startsWith("homech:")){const tiles=homeLiveTiles();const idx=+a.split(":")[1];const preferred=["ERT 1","ERT1","ERT 2","ANT1","ALPHA","SKAI","ΣΚΑΪ","OPEN","MEGA"],picked=[];preferred.forEach(n=>{const c=channels.find(x=>x.name.toUpperCase().includes(n.toUpperCase())&&!picked.some(p=>p.url===x.url));if(c&&picked.length<5)picked.push(c)});channels.forEach(c=>{if(picked.length<5&&!picked.some(p=>p.url===c.url))picked.push(c)});if(picked[idx])play(picked[idx])}else if(a.startsWith("recent:")){const r=recent()[+a.split(":")[1]];const c=channels.find(x=>x.url===r.url);if(c)play(c)}})}
function focusFirst(){setTimeout(()=>{const x=document.querySelector("main button");if(x)x.focus()},30)}
function popularTiles(){
 const defs=[
  ["ERT 1","ERT1","https://i.imgur.com/UKbCtC1.png","#123ec6"],["ERT 2","ERT 2","", "#f2f4f7"],["ANT1","ANT1","https://i.imgur.com/ItxKvVS.png","#194778"],
  ["ALPHA","ALPHA","https://i.imgur.com/6twzd38.png","#f10f56"],["ΣΚΑΪ","SKAI","", "#1268dd"],["OPEN","OPEN","https://upload.wikimedia.org/wikipedia/el/thumb/e/e5/Open_TV_logo.png/960px-Open_TV_logo.png","#182031"],
  ["MEGA","MEGA","https://i.imgur.com/Z3k7iA0.png","#f6f7f8"]
 ];
 return defs.map((d,i)=>{const ch=channels.find(x=>x.name.toUpperCase().replace("ΕΡΤ","ERT").replace("ΣΚΑΪ","SKAI").includes(d[1].toUpperCase()));const sub=ch&&ch.tvgId&&epgNow[ch.tvgId]?"NOW  •  "+epgNow[ch.tvgId]:(ch?ch.name+" HD":d[0]+" HD");const st='--brandbg:'+d[3]+(d[2]?';background-image:url("'+d[2]+'")':'');return tile(d[0],sub,ch?"popular:"+i:"live","popular"+(d[2]?" logo":""),st)})
}
function homeLiveTiles(){
 const preferred=["ERT 1","ERT1","ERT 2","ANT1","ALPHA","SKAI","ΣΚΑΪ","OPEN","MEGA"],picked=[];
 preferred.forEach(n=>{const c=channels.find(x=>x.name.toUpperCase().includes(n.toUpperCase())&&!picked.some(p=>p.url===x.url));if(c&&picked.length<5)picked.push(c)});
 channels.forEach(c=>{if(picked.length<5&&!picked.some(p=>p.url===c.url))picked.push(c)});
 if(!picked.length)return ["ERT 1","ANT1","ALPHA","SKAI","MEGA"].map(n=>tile("●  "+n,"Live TV","live","category",'background:linear-gradient(135deg,#15518d,#0a2f58)'));
 return picked.map((c,i)=>tile("●  "+c.name,c.group||"LIVE TV","homech:"+i,"category",'background:linear-gradient(135deg,#15518d,#0a2f58)'));
}
function recent(){try{return JSON.parse(localStorage.getItem("recent")||"[]")}catch(e){return[]}}
function saveRecent(c){let r=recent().filter(x=>x.url!==c.url);r.unshift({name:c.name,url:c.url,group:c.group,tvgId:c.tvgId||"",watchedAt:Date.now()});localStorage.setItem("recent",JSON.stringify(r.slice(0,4)))}
function favs(){try{return JSON.parse(localStorage.getItem("favs")||"[]")}catch(e){return[]}}
function guide(){stopPreview();$("main").innerHTML='<div class="screenTitle">TV GUIDE</div><div class="channelRows" id="channelRows"></div>';const rows=$("channelRows");channels.slice(0,40).forEach(ch=>{const b=document.createElement("button");b.className="channelRow guideRow";const now=epgNow[ch.tvgId]||"Live programming",next=epgNext[ch.tvgId]||"Schedule unavailable";b.innerHTML='<strong>'+esc(ch.name)+'</strong><span>NOW  •  '+esc(now)+' &nbsp;&nbsp; NEXT • '+esc(next)+'</span>';b.onclick=()=>play(ch);rows.appendChild(b)});setTimeout(()=>{const x=rows.querySelector("button");if(x)x.focus()},30)}
function list(filter=null,favOnly=false){stopPreview();filtered=channels.filter(c=>(!filter||c.group.toLowerCase().includes(filter.toLowerCase()))&&(!favOnly||favs().includes(c.url)));$("main").innerHTML='<div class="screenTitle">'+(favOnly?"FAVOURITES":filter?esc(filter):"LIVE TV")+'</div><div class="channelLayout"><div class="channelList"><div class="channelRows" id="channelRows"></div></div><div class="preview"><video id="preview" muted autoplay></video><div id="previewTitle">Select a channel</div><div id="previewMeta">Live preview</div></div></div>';const rows=$("channelRows");if(!filtered.length){rows.innerHTML='<div class="empty">No channels found.</div>';return}filtered.forEach((c,i)=>{const b=document.createElement("button");b.className="channelRow";b.textContent=c.name+(favs().includes(c.url)?"   ★":"");b.onclick=()=>play(c);b.onfocus=()=>schedulePreview(c);b.oncontextmenu=e=>{e.preventDefault();toggleFav(c);list(filter,favOnly)};b.onkeydown=e=>{if(e.keyCode===461||e.keyCode===8)return;if(e.keyCode===13&&e.ctrlKey){toggleFav(c);list(filter,favOnly)}};rows.appendChild(b)});focusFirst();setTimeout(()=>{const x=rows.querySelector("button");if(x)x.focus()},30)}
function schedulePreview(c){clearTimeout(previewTimer);$("previewTitle").textContent=c.name;$("previewMeta").textContent="Loading preview…";previewTimer=setTimeout(()=>{const v=$("preview");if(!v)return;try{v.src=c.url;v.onplaying=()=>{$("previewMeta").textContent=(c.tvgId&&epgNow[c.tvgId]?"NOW  •  "+epgNow[c.tvgId]:(c.group||"GREEK TV").toUpperCase()+" · LIVE NOW")};v.onerror=()=>{$("previewMeta").textContent="Temporarily unavailable • OK to try full screen"};v.play().catch(()=>{$("previewMeta").textContent="Temporarily unavailable • OK to try full screen"})}catch(e){$("previewMeta").textContent="Temporarily unavailable • OK to try full screen"}},350)}
function stopPreview(){clearTimeout(previewTimer);const v=$("preview");if(v){try{v.pause();v.removeAttribute("src");v.load()}catch(e){}}}
function play(c){stopPreview();current=channels.findIndex(x=>x.url===c.url);saveRecent(c);$("app").classList.add("hidden");$("playerScreen").classList.remove("hidden");$("playerTitle").textContent=c.name;const v=$("video");v.onerror=()=>{$("playerTitle").textContent=c.name+" • Temporarily unavailable";setTimeout(closePlayer,1200)};v.src=c.url;v.play().catch(()=>{$("playerTitle").textContent=c.name+" • Temporarily unavailable"})}
function closePlayer(){const v=$("video");try{v.pause();v.removeAttribute("src");v.load()}catch(e){};$("playerScreen").classList.add("hidden");$("app").classList.remove("hidden");list()}
function continueWatch(){const r=recent()[0];if(!r)return list();const c=channels.find(x=>x.url===r.url);if(c)play(c);else list()}
function toggleFav(c){let f=favs();f=f.includes(c.url)?f.filter(x=>x!==c.url):[...f,c.url];localStorage.setItem("favs",JSON.stringify(f))}
function settings(){alert(B.name+" for LG webOS\nContent refreshes automatically.")}
window.addEventListener("keydown",e=>{if(!$("playerScreen").classList.contains("hidden")){if(e.keyCode===461||e.keyCode===8||e.key==="Escape"){e.preventDefault();closePlayer();return}if((e.keyCode===33||e.keyCode===38)&&channels.length){current=(current+1)%channels.length;play(channels[current])}if((e.keyCode===34||e.keyCode===40)&&channels.length){current=(current-1+channels.length)%channels.length;play(channels[current])}}else if(e.keyCode===461||e.keyCode===8||e.key==="Escape"){e.preventDefault();home()}});
(async()=>{document.body.insertAdjacentHTML("beforeend",'<div id="boot"><div class="bootFlag">🇬🇷</div><div class="bootBrand">'+esc(B.name)+'</div><div class="bootSub">GREEK TELEVISION • '+esc(B.place.toUpperCase())+' • AND MORE</div></div>');initBrand();buildNav();await loadPlaylist();home();setTimeout(()=>{const b=$("boot");if(b){b.style.opacity="0";setTimeout(()=>b.remove(),320)}},650);loadEpg()})();