const http=require("http"),https=require("https"),fs=require("fs"),path=require("path");
const root=__dirname,port=process.env.PORT||3000;
function send(res,status,body,type="application/json"){res.writeHead(status,{"content-type":type,"cache-control":"no-store","access-control-allow-origin":"*"});res.end(body)}
function get(url){return new Promise((resolve,reject)=>{https.get(url,{headers:{"user-agent":"Mozilla/5.0 GreekOne/1.0","accept":"*/*"}},r=>{let b=[];r.on("data",d=>b.push(d));r.on("end",()=>resolve({status:r.statusCode,headers:r.headers,body:Buffer.concat(b)}))}).on("error",reject)})}
function mime(f){return f.endsWith(".html")?"text/html":f.endsWith(".js")?"application/javascript":f.endsWith(".css")?"text/css":"application/octet-stream"}
http.createServer(async(req,res)=>{
 try{
  const u=new URL(req.url,"http://localhost");
  if(u.pathname==="/api/ert-details"){
   const id=u.searchParams.get("id");if(!id||!/^[A-Za-z0-9_-]+$/.test(id))return send(res,400,'{"error":"bad id"}');
   const r=await get("https://live.ertflix.gr/api/details?contentId="+encodeURIComponent(id)+"&lang=en_GB");return send(res,r.status,r.body,"application/json");
  }
  if(u.pathname==="/api/page"){
   const raw=u.searchParams.get("url")||"";let x;try{x=new URL(raw)}catch{return send(res,400,'{"error":"bad url"}')}
   if(!["www.megatv.com","megatv.com"].includes(x.hostname))return send(res,403,'{"error":"host not allowed"}');
   const r=await get(x.href);return send(res,r.status,r.body,r.headers["content-type"]||"text/html");
  }
  let f=path.join(root,decodeURIComponent(u.pathname==="/" ? "/index.html":u.pathname));
  if(!f.startsWith(root))return send(res,403,"forbidden","text/plain");
  fs.readFile(f,(e,b)=>{if(e){fs.readFile(path.join(root,"index.html"),(e2,b2)=>e2?send(res,404,"not found","text/plain"):send(res,200,b2,"text/html"))}else send(res,200,b,mime(f))});
 }catch(e){send(res,500,JSON.stringify({error:"server error"}))}
}).listen(port);
