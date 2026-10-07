# Greek One iOS

Native SwiftUI iPhone/iPad client for Greek One.

## Architecture
- SwiftUI navigation and catalogue UI
- AVKit/AVPlayer for direct HLS playback where iOS permits it
- SFSafariViewController for official broadcaster archive pages
- Local favourites and continue-watching using UserDefaults
- Existing Greek One web service remains independent

## App Store preparation
This branch is intentionally separate from the Android TV and web branches. Before public submission, verify distribution rights for every live stream and archive entry exposed in-app, complete App Store privacy metadata, icons/screenshots, signing and Apple Developer identifiers.

Open the project in Xcode after generating/adding the Xcode project shell. Bundle identifier target: au.com.greektv.ios
