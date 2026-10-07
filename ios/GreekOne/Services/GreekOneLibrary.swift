import Foundation

@MainActor final class GreekOneLibrary: ObservableObject {
    @Published var items: [MediaItem] = []
    @Published var favourites: Set<String> = Set(UserDefaults.standard.stringArray(forKey: "GreekOneFavourites") ?? [])
    @Published var recent: [String] = UserDefaults.standard.stringArray(forKey: "GreekOneRecent") ?? []

    var live: [MediaItem] { items.filter { $0.kind == .live } }
    var movies: [MediaItem] { items.filter { $0.kind == .movie } }
    var series: [MediaItem] { items.filter { $0.kind == .series } }
    var cooking: [MediaItem] { items.filter { $0.kind == .cooking } }

    init() { loadSeed() }

    func toggleFavourite(_ item: MediaItem) {
        if favourites.contains(item.id) { favourites.remove(item.id) } else { favourites.insert(item.id) }
        UserDefaults.standard.set(Array(favourites), forKey: "GreekOneFavourites")
    }

    func remember(_ item: MediaItem) {
        recent.removeAll { $0 == item.id }; recent.insert(item.id, at: 0)
        recent = Array(recent.prefix(20)); UserDefaults.standard.set(recent, forKey: "GreekOneRecent")
    }

    private func loadSeed() {
        guard let url = Bundle.main.url(forResource: "catalogue", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([MediaItem].self, from: data) else { return }
        items = decoded
    }
}
