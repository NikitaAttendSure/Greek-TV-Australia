import Foundation

enum MediaKind: String, Codable, CaseIterable { case live, movie, series, cooking }

struct MediaItem: Identifiable, Codable, Hashable {
    let id: String
    let title: String
    let subtitle: String
    let kind: MediaKind
    let url: URL
    let artwork: URL?
    var broadcaster: String? = nil
    var year: String? = nil
    var synopsis: String? = nil
}
