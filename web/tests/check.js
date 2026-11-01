"use strict";
const assert = require("node:assert/strict");
const { parseEpisodes, FALLBACK_EPISODE } = require("../skai-series");

const sample = [
  '<a href="/tv/episode/seires/mple-ores/2026-09-14-21/mple-ores-epeisodio-1-14092026" class="resio">',
  ' <img src="https://media.skaitv.gr/images/250/0/media_tv-editors/ble_wres_landscape1.jpg" alt="Μπλε Ώρες | Επεισόδιο 1">',
  '</a>',
  '<a class="resio" href="/tv/episode/seires/mple-ores/2026-10-16-21/mple-ores-epeisodio-31-16102026">',
  ' <img alt="Μπλε Ώρες | Επεισόδιο 31" src="https://media.skaitv.gr/images/250/0/media_tv-editors/ble_wres_landscape1.jpg">',
  '</a>',
  '<a href="https://malicious.example/tv/episode/seires/mple-ores/000" class="resio"><img alt="Μπλε Ώρες | Επεισόδιο 99"></a>',
  '<a href="/tv/episode/seires/mple-ores/2026-09-14-21/mple-ores-epeisodio-1-14092026" class="resio">',
  ' <img alt="Μπλε Ώρες | Επεισόδιο 1"></a>'
].join("\n");

const episodes = parseEpisodes(sample);
assert.deepEqual(episodes.map(x => x.number), [31, 1], "sort, de-duplicate and limit to the official publisher");
assert.match(episodes[0].url, /^https:\/\/www\.skai\.gr\/tv\/episode\/seires\/mple-ores\//);
assert.match(episodes[0].image, /^https:\/\/media\.skaitv\.gr/);
assert.deepEqual(parseEpisodes(""), [], "empty response has no fake episodes");
assert.equal(FALLBACK_EPISODE.number, 1);
assert.match(FALLBACK_EPISODE.url, /mple-ores-epeisodio-1/);
console.log("PASS Greek One SKAI episode parser, publisher URL allowlist and fallback.");
