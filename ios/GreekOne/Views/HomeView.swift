import SwiftUI

struct HomeView: View {
    @EnvironmentObject var library: GreekOneLibrary
    var featured: MediaItem? { library.series.first ?? library.movies.first ?? library.live.first }
    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 28) {
                    if let item = featured { FeaturedHero(item: item) }
                    Rail(title: "Live Now", items: Array(library.live.prefix(12)))
                    Rail(title: "Greek Cinema", items: Array(library.movies.prefix(12)))
                    Rail(title: "Iconic Greek Series", items: Array(library.series.prefix(12)))
                    Rail(title: "Greek Kitchen", items: Array(library.cooking.prefix(12)))
                }.padding(.bottom, 30)
            }
            .background(LinearGradient(colors: [.black, Color(red: 0.01, green: 0.08, blue: 0.13)], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
            .navigationTitle("GREEK ONE")
        }
    }
}

struct FeaturedHero: View {
    let item: MediaItem
    var body: some View {
        NavigationLink(value: item) {
            ZStack(alignment: .bottomLeading) {
                AsyncImage(url: item.artwork) { image in image.resizable().scaledToFill() } placeholder: { Rectangle().fill(.gray.opacity(0.15)) }
                LinearGradient(colors: [.clear,.black.opacity(0.95)], startPoint: .center, endPoint: .bottom)
                VStack(alignment: .leading, spacing: 8) { Text("FEATURED TONIGHT").font(.caption.bold()).foregroundStyle(.cyan); Text(item.title).font(.largeTitle.bold()); Text(item.subtitle).foregroundStyle(.secondary) }.padding(22)
            }.frame(height: 390).clipShape(RoundedRectangle(cornerRadius: 24))
        }.buttonStyle(.plain).padding(.horizontal)
    }
}

struct Rail: View {
    let title: String; let items: [MediaItem]
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.title2.bold()).padding(.horizontal)
            ScrollView(.horizontal, showsIndicators: false) { LazyHStack(spacing: 12) { ForEach(items) { item in NavigationLink(value:item) { PosterCard(item:item) }.buttonStyle(.plain) } }.padding(.horizontal) }
        }
        .navigationDestination(for: MediaItem.self) { MediaDetailView(item: $0) }
    }
}

struct PosterCard: View {
    let item: MediaItem
    var body: some View {
        VStack(alignment:.leading,spacing:7) {
            AsyncImage(url:item.artwork) { image in image.resizable().scaledToFill() } placeholder: { Rectangle().fill(.white.opacity(0.06)) }
                .frame(width:180,height:250).clipped().clipShape(RoundedRectangle(cornerRadius:16))
            Text(item.title).font(.subheadline.bold()).lineLimit(1).frame(width:180,alignment:.leading)
            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1).frame(width:180,alignment:.leading)
        }
    }
}
