import SwiftUI
import AVKit
import SafariServices

struct MediaDetailView: View {
 @EnvironmentObject var library:GreekOneLibrary
 let item:MediaItem
 @State private var play=false
 var body:some View{ScrollView{VStack(alignment:.leading,spacing:16){AsyncImage(url:item.artwork){$0.resizable().scaledToFill()}placeholder:{Rectangle().fill(.white.opacity(0.06))}.frame(height:360).clipped();Text(item.title).font(.largeTitle.bold());Text(item.subtitle).foregroundStyle(.secondary);if let synopsis=item.synopsis{Text(synopsis).font(.body)};Button{library.remember(item);play=true}label:{Label(item.kind == .live ? "Watch Live":"Watch",systemImage:"play.fill").frame(maxWidth:.infinity).padding()}.buttonStyle(.borderedProminent).tint(.cyan);Button{library.toggleFavourite(item)}label:{Label(library.favourites.contains(item.id) ? "Remove Favourite":"Add to Favourites",systemImage:library.favourites.contains(item.id) ? "heart.fill":"heart")}.buttonStyle(.bordered)}.padding()}.navigationBarTitleDisplayMode(.inline).sheet(isPresented:$play){if item.kind == .live {VideoPlayer(player:AVPlayer(url:item.url)).ignoresSafeArea()} else {SafariView(url:item.url).ignoresSafeArea()}}}
}

struct SafariView:UIViewControllerRepresentable{let url:URL;func makeUIViewController(context:Context)->SFSafariViewController{SFSafariViewController(url:url)};func updateUIViewController(_ uiViewController:SFSafariViewController,context:Context){}}
