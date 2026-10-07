# Greek TV — Sony / Google TV

Release candidate 0.4.0.

## Grandparent-first controls
- Home: large Greek-labelled sections.
- LIVE TV: D-pad to choose a channel, OK to play.
- While watching: Channel Up / D-pad Up = next channel; Channel Down / D-pad Down = previous.
- Star/Bookmark remote key toggles favourite where supported.
- Back returns to the channel list.
- Continue resumes the last selected channel.
- Buffering indicator is shown automatically.
- Broken streams return a simple Greek error instead of leaving a black screen.

## Content
The app reads the existing public Greek Mix M3U remotely and does not modify it.
Protected VOD is not extracted or republished; provider-controlled content opens via authorised external destination.

## Release boundary
The remaining validation is physical-device testing on the target Sony Google TV: install APK, launch from TV home, D-pad/focus check, HLS/HTTP playback, channel switching, favourites, resume and external provider hand-off.

Final RESKAKIS TV launcher-brand build verification.
