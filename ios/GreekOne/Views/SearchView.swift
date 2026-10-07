import SwiftUI

struct SearchView: View {
 @EnvironmentObject var library:GreekOneLibrary
 @State private var query=""
 var results:[MediaItem]{query.isEmpty ? [] : library.items.filter{$0.title.localizedCaseInsensitiveContains(query)||$0.subtitle.localizedCaseInsensitiveContains(query)}}
 var body:some View{NavigationStack{List(results){item in NavigationLink(value:item){VStack(alignment:.leading){Text(item.title).font(.headline);Text(item.subtitle).font(.caption).foregroundStyle(.secondary)}}}.navigationTitle("Search").searchable(text:$query).navigationDestination(for:MediaItem.self){MediaDetailView(item:$0)}}}
}
