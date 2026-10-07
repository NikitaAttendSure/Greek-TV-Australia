# Greek One — App Store release plan

## Commercial model
- Paid download target: A$4.99
- No subscription
- No advertising
- Price is configured in App Store Connect, not hard-coded in the app.

## Submission gates
1. Apple Developer Program membership and App Store Connect app record.
2. Final signing, bundle ID, icons, screenshots, privacy/support URLs.
3. Verify distribution rights/permission for every live stream played directly in-app.
4. Verify broadcaster terms for archive links and artwork.
5. Physical iPhone/iPad playback and network testing.
6. App Review notes documenting sources/permissions.

## Review-safe architecture
Official archive titles open their broadcaster pages using SFSafariViewController. Direct AVPlayer playback should be limited to streams whose third-party distribution is permitted. Do not imply ownership of broadcaster programming or trademarks.

## Store draft
Name: Greek One
Subtitle: Greek TV, Cinema & Series
Price target: A$4.99
Category: Entertainment

Greek One brings Greek entertainment together in a polished experience built for iPhone and iPad. Explore live Greek television, classic cinema and iconic series, save favourites and continue where you left off. Archive programmes remain with their official broadcasters.
