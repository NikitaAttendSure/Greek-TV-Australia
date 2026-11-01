"use strict";

// Read only programme metadata (episode numbers, official links and thumbnail URLs).
// No video streams, advertising, consent, or DRM are proxied.
const https = require("https");
const OFFICIAL_SHOW = "https://www.skai.gr/tv/show/seires/mple-ores/sezon-2026-2027/";
const EPISODE_PREFIX = "https://www.skai.gr/tv/episode/seires/mple-ores/";
const INDEX_PREFIX = "https://www.skai.gr/tv/ajaxshowsepisode/68902/";
const FALLBACK_EPISODE = {
  number: 1,
  title: "Μπλε Ώρες | Επεισόδιο 1",
  url: "https://www.skai.gr/tv/episode/seires/mple-ores/2026-09-14-21/mple-ores-epeisodio-1-14092026",
  image: "https://media.skaitv.gr/images/250/0/media_tv-editors/ble_wres_landscape1.jpg"
};
const MAX_RESPONSE_BYTES = 900_000;
const MAX_PAGES = 6;
const CACHE_MS = 15 * 60 * 1000;
let cache = { timestamp: 0, episodes: [] };
let pending = null;

function attribute(tag, name) {
  const match = tag.match(new RegExp("\\b" + name + "\\s*=\\s*([\"'])(.*?)\\1", "i"));
  return match ? match[2].replace(/&amp;/g, "&") : "";
}

function parseEpisodes(markup) {
  const found = new Map();
  const links = /<a\b([^>]*\bclass\s*=\s*["'][^"']*\bresio\b[^"']*["'][^>]*)>([\s\S]*?)<\/a>/gi;
  let m;
  while ((m = links.exec(String(markup || ""))) !== null) {
    const href = attribute(m[1], "href");
    let official;
    try { official = new URL(href, "https://www.skai.gr"); } catch { continue; }
    if (official.protocol !== "https:" || official.hostname !== "www.skai.gr" ||
        !official.pathname.startsWith("/tv/episode/seires/mple-ores/")) continue;
    const imageTag = m[2].match(/<img\b[^>]*>/i)?.[0] || "";
    const alt = attribute(imageTag, "alt");
    const plain = (alt + " " + m[2].replace(/<[^>]*>/g, " ")).replace(/\s+/g, " ");
    const ep = plain.match(/(?:Επεισόδιο|Episode)\s*([0-9]{1,3})/i);
    const number = ep ? Number(ep[1]) : null;
    if (!number || number < 1 || number > 500 || found.has(number)) continue;
    let image = attribute(imageTag, "src");
    try {
      const imgUrl = new URL(image, "https://media.skaitv.gr");
      image = imgUrl.protocol === "https:" && (imgUrl.hostname === "media.skaitv.gr" || imgUrl.hostname === "www.skai.gr")
        ? imgUrl.href : "";
    } catch { image = ""; }
    found.set(number, { number, title: "Μπλε Ώρες | Επεισόδιο " + number, url: official.href, image });
  }
  return [...found.values()].sort((a, b) => b.number - a.number);
}

function fetchIndex(page) {
  return new Promise((resolve, reject) => {
    const req = https.get(INDEX_PREFIX + page, {
      headers: {
        "User-Agent": "Mozilla/5.0 GreekOneSeriesCatalogue/1.0",
        "Referer": OFFICIAL_SHOW,
        "X-Requested-With": "XMLHttpRequest",
        "Accept": "text/html, */*"
      },
      timeout: 9000
    }, res => {
      if (res.statusCode !== 200) { res.resume(); return reject(new Error("SKAI HTTP " + res.statusCode)); }
      let size = 0;
      const chunks = [];
      res.on("data", chunk => {
        size += chunk.length;
        if (size > MAX_RESPONSE_BYTES) {
          req.destroy(new Error("SKAI page too large"));
          return;
        }
        chunks.push(chunk);
      });
      res.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
      res.on("error", reject);
    });
    req.on("timeout", () => req.destroy(new Error("SKAI timeout")));
    req.on("error", reject);
  });
}

async function listEpisodes() {
  if (Date.now() - cache.timestamp < CACHE_MS && cache.episodes.length) return cache.episodes;
  if (!pending) {
    pending = (async () => {
      const pages = await Promise.allSettled(Array.from({ length: MAX_PAGES }, (_, i) => fetchIndex(i)));
      const episodes = new Map();
      let successfulPages = 0;
      for (const item of pages) {
        if (item.status !== "fulfilled") continue;
        successfulPages++;
        for (const episode of parseEpisodes(item.value)) episodes.set(episode.number, episode);
      }
      if (episodes.size) {
        cache = { timestamp: Date.now(), episodes: [...episodes.values()].sort((a, b) => b.number - a.number) };
        return cache.episodes;
      }
      if (cache.episodes.length) return cache.episodes; // Retain known working index during a brief SKAI outage.
      if (!successfulPages) throw new Error("SKAI episode index unavailable");
      return [];
    })().finally(() => { pending = null; });
  }
  return pending;
}

module.exports = { parseEpisodes, listEpisodes, FALLBACK_EPISODE, OFFICIAL_SHOW, EPISODE_PREFIX };
