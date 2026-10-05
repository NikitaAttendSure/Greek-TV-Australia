#!/usr/bin/env python3
import concurrent.futures, json, re, ssl, time, urllib.request, urllib.error
from pathlib import Path
PLAYLIST=Path("greek-tv.m3u"); TIMEOUT=12
def parse():
    out=[]; meta=None
    for raw in PLAYLIST.read_text(encoding="utf-8",errors="replace").splitlines():
        line=raw.strip()
        if line.startswith("#EXTINF"): meta=line
        elif line.startswith(("http://","https://")) and meta:
            name=meta.rsplit(",",1)[-1].strip()
            g=re.search(r'group-title="([^"]*)"',meta)
            out.append({"name":name,"group":g.group(1) if g else "","url":line}); meta=None
    return out
def check(e):
    req=urllib.request.Request(e["url"],headers={"User-Agent":"Mozilla/5.0 (Linux; Android TV)","Accept":"application/vnd.apple.mpegurl,application/x-mpegURL,video/*,*/*;q=0.8","Range":"bytes=0-32767"})
    t=time.time()
    try:
        with urllib.request.urlopen(req,timeout=TIMEOUT,context=ssl.create_default_context()) as r:
            data=r.read(32768); status=getattr(r,"status",200) or 200; ctype=(r.headers.get("Content-Type") or "").lower()
        text=data[:5000].decode("utf-8","ignore")
        ok=200<=status<400 and ("#EXTM3U" in text or "mpegurl" in ctype or ctype.startswith(("video/","audio/","application/octet-stream")) or len(data)>1024)
        return {**e,"ok":ok,"status":status,"ms":round((time.time()-t)*1000),"detail":"OK" if ok else "Unexpected response"}
    except urllib.error.HTTPError as ex:
        return {**e,"ok":False,"status":ex.code,"ms":round((time.time()-t)*1000),"detail":f"HTTP {ex.code}"}
    except Exception as ex:
        return {**e,"ok":False,"status":0,"ms":round((time.time()-t)*1000),"detail":type(ex).__name__+": "+str(ex)[:120]}
entries=parse()
with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool: results=list(pool.map(check,entries))
good=[r for r in results if r["ok"]]; bad=[r for r in results if not r["ok"]]
Path("channel-audit.json").write_text(json.dumps({"total":len(results),"working":len(good),"failed":len(bad),"results":results},indent=2,ensure_ascii=False),encoding="utf-8")
lines=["# Greek One Channel Audit","",f"- Total: **{len(results)}**",f"- Working/reachable: **{len(good)}**",f"- Failed/suspicious: **{len(bad)}**","","## Failed / suspicious","","| Channel | Group | Status | Detail |","|---|---|---:|---|"]
for r in bad: lines.append(f"| {r['name'].replace('|','/')} | {r['group'].replace('|','/')} | {r['status']} | {r['detail'].replace('|','/')} |")
Path("channel-audit.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
print(json.dumps({"total":len(results),"working":len(good),"failed":len(bad)}))
