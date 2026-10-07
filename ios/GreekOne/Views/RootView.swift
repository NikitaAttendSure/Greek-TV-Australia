import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            HomeView().tabItem { Label("Home", systemImage: "house.fill") }
            LibraryView(kind: .live, title: "Live TV").tabItem { Label("Live", systemImage: "play.tv.fill") }
            LibraryView(kind: .movie, title: "Movies").tabItem { Label("Movies", systemImage: "film.fill") }
            LibraryView(kind: .series, title: "Series").tabItem { Label("Series", systemImage: "rectangle.stack.fill") }
            SearchView().tabItem { Label("Search", systemImage: "magnifyingglass") }
        }.tint(.cyan)
    }
}
