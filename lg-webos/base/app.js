const B=window.TV_BRAND;
const PLAYLIST="https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u";
let channels=[],filtered=[],current=-1,previewTimer=null;
const $=id=>document.getElementById(id);
function esc(s){return String(s||"").replace(/[&<>"]/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;"}[m]))}
function clock(){const d=new Date();$("clock").textContent=d.toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"})+"  |  "+d.toLocaleDateString([],{weekday:"short",day:"numeric",month:"short"})}
function initBrand(){$("brandName").textContent=B.name;$("sub").textContent="GREEK TELEVISION · "+B.place.toUpperCase()+" · AND MORE";$("tagline").textContent="From "+B.place+" to the World";$("hero").style.backgroundImage='url("'+B.hero+'")';clock();setInterval(clock,30000)}
function parseM3U(txt){let out=[],meta=null;txt.split(/\r?\n/).forEach(l=>{if(l.startsWith("#EXTINF")){const name=l.split(",").pop().trim();const g=(l.match(/group-title="([^"]*)"/)||[])[1]||"";const id=(l.match(/tvg-id="([^"]*)"/)||[])[1]||"";meta={name,group:g,tvgId:id}}else if(/^https?:/.test(l)&&meta){out.push({...meta,url:l.trim()});meta=null}});return out}
async function loadPlaylist(){try{const r=await fetch(PLAYLIST,{cache:"no-store"});channels=parseM3U(await r.text());localStorage.setItem("playlist",JSON.stringify(channels))}catch(e){try{channels=JSON.parse(localStorage.getItem("playlist")||"[]")}catch(_){channels=[]}}}
function navButton(label,fn){const b=document.createElement("button");b.className="navBtn";b.textContent=label;b.onclick=fn;return b}
function buildNav(){const n=$("nav");n.innerHTML="";[
["⌂   Home",home],["▣   Live TV",()=>list()],["♥   Favourites",()=>list(null,true)],["◷   Continue",continueWatch],
["▤   On Demand",()=>list("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ")],["◉   "+B.place,()=>list(B.localFilter)],["◎   World TV",()=>list("ΔΙΕΘΝΗ")],
["☷   Categories",home],["⌕   Search",()=>list()],["⚙   Settings",settings]
].forEach(x=>n.appendChild(navButton(x[0],x[1])))}
function section(title,items){return '<div class="sectionTitle">'+esc(title)+'</div><div class="row">'+items.join("")+"</div>"}
function tile(title,sub,action,cls=""){return '<button class="tile '+cls+'" data-action="'+action+'"><strong>'+esc(title)+'</strong><small>'+esc(sub)+'</small></button>'}
function home(){stopPreview();$("main").innerHTML=
section("Popular Greek Channels",["ERT 1","ERT 2","ANT1","ALPHA","SKAI","OPEN","MEGA"].map(x=>tile(x,x+" HD","live"))) +
section("Continue Watching",recent().length?recent().slice(0,4).map((x,i)=>tile(x.name,"Recently watched","recent:"+i)): [tile("Start watching","Your recent channels will appear here","live")])+
section("Browse by Category",[tile("▣  Greek TV","All Greek Channels","live"),tile("◉  Movies","Greek & International","movies"),tile("★  Kids","For the Little Ones","kids"),tile("◉  "+B.place,"Local Content","local"),tile("◎  World TV","International Channels","world")])+
section(B.place+" Highlights",[tile(B.place+" Live","Local Content","local"),tile(B.place+" Highlights","Explore","local"),tile(B.place+" Coast","Seaside","local"),tile(B.place+" Documentary","History","local")]);
bindActions();focusFirst()}
function bindActions(){$("main").querySelectorAll("[data-action]").forEach(b=>b.onclick=()=>{const a=b.dataset.action;if(a==="live")list();else if(a==="movies")list("ΕΛΛΗΝΙΚΕΣ ΤΑΙΝΙΕΣ");else if(a==="kids")list("ΠΑΙΔΙΚΑ");else if(a==="local")list(B.localFilter);else if(a==="world")list("ΔΙΕΘΝΗ");else if(a.startsWith("recent:")){const r=recent()[+a.split(":")[1]];const c=channels.find(x=>x.url===r.url);if(c)play(c)}})}
function focusFirst(){setTimeout(()=>{const x=document.querySelector("main button");if(x)x.focus()},30)}
function recent(){try{return JSON.parse(localStorage.getItem("recent")||"[]")}catch(e){return[]}}
function saveRecent(c){let r=recent().filter(x=>x.url!==c.url);r.unshift({name:c.name,url:c.url,group:c.group});localStorage.setItem("recent",JSON.stringify(r.slice(0,4)))}
function favs(){try{return JSON.parse(localStorage.getItem("favs")||"[]")}catch(e){return[]}}
function list(filter=null,favOnly=false){stopPreview();filtered=channels.filter(c=>(!filter||c.group.toLowerCase().includes(filter.toLowerCase()))&&(!favOnly||favs().includes(c.url)));$("main").innerHTML='<div class="screenTitle">'+(favOnly?"FAVOURITES":filter?esc(filter):"LIVE TV")+'</div><div class="channelLayout"><div class="channelList"><div class="channelRows" id="channelRows"></div></div><div class="preview"><video id="preview" muted autoplay></video><div id="previewTitle">Select a channel</div><div id="previewMeta">Live preview</div></div></div>';const rows=$("channelRows");if(!filtered.length){rows.innerHTML='<div class="empty">No channels found.</div>';return}filtered.forEach((c,i)=>{const b=document.createElement("button");b.className="channelRow";b.textContent=c.name+(favs().includes(c.url)?"   ★":"");b.onclick=()=>play(c);b.onfocus=()=>schedulePreview(c);b.oncontextmenu=e=>{e.preventDefault();toggleFav(c);list(filter,favOnly)};b.onkeydown=e=>{if(e.keyCode===461||e.keyCode===8)return;if(e.keyCode===13&&e.ctrlKey){toggleFav(c);list(filter,favOnly)}};rows.appendChild(b)});focusFirst();setTimeout(()=>{const x=rows.querySelector("button");if(x)x.focus()},30)}
function schedulePreview(c){clearTimeout(previewTimer);$("previewTitle").textContent=c.name;$("previewMeta").textContent=(c.group||"GREEK TV").toUpperCase()+" · LIVE NOW";previewTimer=setTimeout(()=>{const v=$("preview");if(!v)return;try{v.src=c.url;v.play().catch(()=>{})}catch(e){}},350)}
function stopPreview(){clearTimeout(previewTimer);const v=$("preview");if(v){try{v.pause();v.removeAttribute("src");v.load()}catch(e){}}}
function play(c){stopPreview();current=channels.findIndex(x=>x.url===c.url);saveRecent(c);$("app").classList.add("hidden");$("playerScreen").classList.remove("hidden");$("playerTitle").textContent=c.name;const v=$("video");v.src=c.url;v.play().catch(()=>{})}
function closePlayer(){const v=$("video");try{v.pause();v.removeAttribute("src");v.load()}catch(e){};$("playerScreen").classList.add("hidden");$("app").classList.remove("hidden");list()}
function continueWatch(){const r=recent()[0];if(!r)return list();const c=channels.find(x=>x.url===r.url);if(c)play(c);else list()}
function toggleFav(c){let f=favs();f=f.includes(c.url)?f.filter(x=>x!==c.url):[...f,c.url];localStorage.setItem("favs",JSON.stringify(f))}
function settings(){alert(B.name+" for LG webOS\nContent refreshes automatically.")}
window.addEventListener("keydown",e=>{if(!$("playerScreen").classList.contains("hidden")){if(e.keyCode===461||e.keyCode===8||e.key==="Escape"){e.preventDefault();closePlayer();return}if((e.keyCode===33||e.keyCode===38)&&channels.length){current=(current+1)%channels.length;play(channels[current])}if((e.keyCode===34||e.keyCode===40)&&channels.length){current=(current-1+channels.length)%channels.length;play(channels[current])}}else if(e.keyCode===461||e.keyCode===8||e.key==="Escape"){e.preventDefault();home()}});
(async()=>{initBrand();buildNav();await loadPlaylist();home()})();