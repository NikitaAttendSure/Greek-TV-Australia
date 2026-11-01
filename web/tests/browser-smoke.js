"use strict";
const assert = require("node:assert/strict");
const {chromium} = require("playwright");

(async () => {
 const browser = await chromium.launch({headless:true,args:["--no-sandbox"]});
 try {
  const page = await browser.newPage({viewport:{width:1300,height:850}});
  const problems=[];
  page.on("pageerror",err=>problems.push(err.message));
  await page.goto("http://127.0.0.1:3999/",{waitUntil:"domcontentloaded",timeout:16000});
  await page.locator("aside button[data-view='series']").click();
  assert.equal(await page.locator("#grid>.card").count(),31);
  assert.match(await page.locator("#hero").innerText(),/31 SERIES/);
  await page.locator("#grid>.card").filter({hasText:"ΜΠΛΕ ΩΡΕΣ"}).first().click();
  assert.equal(await page.locator("#loadSkaiEpisodes").count(),1);
  await page.locator("#loadSkaiEpisodes").click();
  await page.locator(".skai-episode-row").first().waitFor({timeout:22000});
  const count=await page.locator(".skai-episode-row").count();
  assert.ok(count>=1);
  await page.locator(".skai-episode-row").last().click();
  assert.equal(await page.locator(".skai-player-frame iframe").count(),1);
  assert.match(await page.locator(".skai-player-frame iframe").getAttribute("src"),/^https:\/\/www\.skai\.gr\/tv\/episode\/seires\/mple-ores\//);
  assert.equal(await page.locator(".skai-official-link").count(),1);
  await page.locator(".live-back").click();
  assert.equal(await page.locator("#loadSkaiEpisodes").count(),1);
  console.log("DESKTOP: 31 series, SKAI episode rows="+count+", in-site player and Back OK");
  for(const section of ["live","movies","cooking"]){
   await page.locator("aside button[data-view='"+section+"']").first().click();
   const n=await page.locator("#grid>.card").count();
   assert.ok(n>0,section+" has no cards");
   console.log(section.toUpperCase()+": "+n+" cards");
  }
  assert.deepEqual(problems,[]);
  const mobile=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:2});
  mobile.on("pageerror",err=>problems.push(err.message));
  await mobile.goto("http://127.0.0.1:3999/",{waitUntil:"domcontentloaded",timeout:16000});
  await mobile.locator(".mobile-nav button[data-view='series']").click();
  assert.equal(await mobile.locator("#grid>.card").count(),31);
  await mobile.locator("#grid>.card").filter({hasText:"ΜΠΛΕ ΩΡΕΣ"}).first().click();
  await mobile.locator("#loadSkaiEpisodes").click();
  await mobile.locator(".skai-episode-row").first().waitFor({timeout:22000});
  assert.ok(await mobile.locator(".skai-episode-row").count()>=1);
  const overflow=await mobile.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth);
  assert.ok(overflow<=10,"mobile overflow="+overflow);
  assert.deepEqual(problems,[]);
  console.log("MOBILE: series and episode list OK, overflow="+overflow);
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
