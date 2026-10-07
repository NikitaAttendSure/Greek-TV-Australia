import SwiftUI

@main
struct GreekOneApp: App {
    @StateObject private var library = GreekOneLibrary()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(library).preferredColorScheme(.dark) }
    }
}
