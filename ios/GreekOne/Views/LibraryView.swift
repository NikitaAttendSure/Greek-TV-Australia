import SwiftUI

struct LibraryView: View {
    @EnvironmentObject var library: GreekOneLibrary
    let kind: MediaKind; let title: String
    var items:[MediaItem] { library.items.filter{$0.kind == kind} }
    let cols=[GridItem(.adaptive(minimum:150),spacing:14)]
    var body: some View {
        NavigationStack {
            ScrollView { LazyVGrid(columns:cols,spacing:18) { ForEach(items) { item in NavigationLink(value:item) { PosterCard(item:item) }.buttonStyle(.plain) } }.padding() }
                .navigationTitle(title).navigationDestination(for:MediaItem.self){MediaDetailView(item:$0)}
        }
    }
}
