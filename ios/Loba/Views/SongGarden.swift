import SwiftUI

private let songColors: [Color] = [.sky, .plum, .coral, .leaf, Color(hex:"e2a528"), Color(hex:"eb73a6")]

/// A little record sleeve, drawn natively so every screen size stays crisp.
private struct SongSleeve: View {
    var color: Color
    var symbol: String = "music.note"
    var large = false
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius:large ? 30 : 20).fill(color.opacity(0.18))
            GeometryReader { geo in
                Circle().fill(color.opacity(0.13)).frame(width:geo.size.width*0.9).offset(x:geo.size.width*0.55,y:-geo.size.height*0.3)
                Circle().fill(Color.white.opacity(0.6)).frame(width:geo.size.width*0.8).offset(x:-geo.size.width*0.4,y:geo.size.height*0.55)
            }.clipped()
            ZStack {
                Circle().fill(Color.ink)
                ForEach(0..<3) { n in Circle().stroke(Color.white.opacity(0.14),lineWidth:1).padding(CGFloat(10+n*8)) }
                Circle().fill(color).padding(large ? 32 : 23)
                Image(systemName:symbol).font(.system(size:large ? 32 : 24,weight:.bold)).foregroundStyle(.white)
            }.padding(large ? 40 : 20)
            VStack { Spacer(); KubaBand(color:color,background:color.opacity(0.08),height:12) }
        }.clipShape(RoundedRectangle(cornerRadius:large ? 30 : 20)).accessibilityHidden(true)
    }
}

struct SongGarden: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scene
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var library = SongLibrary()
    @State private var favoritesOnly = false
    @State private var born = Date()
    private var visible: [LibrarySong] { library.songs.filter { !favoritesOnly || library.favorites.contains($0.id) } }
    private var accent: Color { songColors[library.selected?.artwork ?? 0] }

    var body: some View {
        ScrollView {
            VStack(alignment:.leading,spacing:22) {
                header
                stage
                if library.loading { ProgressView(L("On prépare la musique…", "Warming up the music…")).frame(maxWidth:.infinity).padding() }
                if library.error {
                    VStack(spacing:10) {
                        Text(L("La chanson n’arrive pas. Vérifie ta connexion et réessaie.", "The song couldn’t load. Check your connection and try again.")).font(.text(15)).multilineTextAlignment(.center)
                        Button(L("Réessayer", "Try again")) { Task { if let song = library.selected { library.play(song) } else { await library.load() } } }.buttonStyle(.chunky(.sky))
                    }.padding().card()
                }
                if !library.songs.isEmpty {
                    HStack(spacing:8) {
                        filter(L("Toutes", "All songs"),active:!favoritesOnly) { favoritesOnly = false }
                        filter(L("Mes favoris", "My favorites"),active:favoritesOnly) { favoritesOnly = true }
                        Spacer(minLength:0)
                        Text("\(visible.count)").font(.text(16,.heavy)).foregroundStyle(Color.ink2)
                    }
                    LazyVGrid(columns:[GridItem(.adaptive(minimum:145),spacing:16)],spacing:18) {
                        ForEach(visible) { song in songCard(song) }
                    }
                }
                if !library.loading && visible.isEmpty && !library.error {
                    VStack(spacing:10) {
                        Art(name:"heart",size:42)
                        Text(favoritesOnly ? L("Tes chansons préférées t’attendent ici.", "Your favorite songs will live here.") : L("Les chansons arrivent bientôt !", "Songs are on their way!")).font(.text(17,.heavy))
                        if favoritesOnly { Text(L("Touche le cœur d’une chanson pour la garder.", "Tap a song’s heart to save it here.")).font(.text(14)).foregroundStyle(Color.ink2) }
                    }.frame(maxWidth:.infinity).multilineTextAlignment(.center).padding(28)
                }
                HStack { Spacer(); Text("🇨🇩"); Text(L("Les chansons de chez nous", "The songs we grew up with")).font(.text(13)); Spacer() }.foregroundStyle(Color.ink2).padding(.vertical,12)
            }.frame(maxWidth:760).padding(20).frame(maxWidth:.infinity)
        }
        .background(Color(hex:"fcf9f2").ignoresSafeArea())
        .safeAreaInset(edge:.bottom) {
            if let song = library.selected {
                HStack(spacing:12) {
                    Image(systemName:"music.note").font(.system(size:22,weight:.bold)).foregroundStyle(Color.sun).frame(width:32)
                    VStack(alignment:.leading,spacing:2) {
                        Text(L("À L’ÉCOUTE", "NOW PLAYING")).font(.text(10,.heavy)).tracking(1).foregroundStyle(Color.white.opacity(0.65))
                        Text(song.title).font(.text(16,.heavy)).foregroundStyle(.white).lineLimit(2)
                    }.frame(maxWidth:.infinity,alignment:.leading)
                    Button { library.toggle() } label: { Image(systemName:library.playing ? "pause.fill" : "play.fill").font(.system(size:20,weight:.bold)).foregroundStyle(Color.ink).frame(width:48,height:48).background(Color.sun,in:Circle()) }.accessibilityLabel(library.playing ? L("Pause", "Pause") : L("Écouter", "Play"))
                    Button { library.next() } label: { Image(systemName:"forward.end.fill").font(.system(size:20)).foregroundStyle(.white).frame(width:44,height:48) }.accessibilityLabel(L("Chanson suivante", "Next song"))
                }.padding(14).background(Color.ink,in:RoundedRectangle(cornerRadius:24)).padding(.horizontal,16).padding(.vertical,8)
            }
        }
        .task { await library.open() }
        .onDisappear { library.close() }
        .onChange(of:scene) { _, value in if value != .active { library.pause() } }
    }
    private var header: some View {
        HStack(alignment:.top,spacing:14) {
            VStack(alignment:.leading,spacing:5) {
                Text(L("LE COIN MUSIQUE", "LOBA’S MUSIC CORNER")).font(.text(11,.heavy)).tracking(2).foregroundStyle(Color.plum)
                Text(L("Le jardin des chansons", "The Song Garden")).font(.display(30)).foregroundStyle(Color.ink)
                Text(L("Écoute. Danse. Chante avec nous !", "Listen. Dance. Sing along!")).font(.text(16)).foregroundStyle(Color.ink2)
            }
            Spacer(minLength:0)
            roundButton("xmark",L("Fermer", "Close")) { dismiss() }
        }
    }
    private var stage: some View {
        VStack(spacing:0) {
            TimelineView(.animation(minimumInterval:1.0/20,paused:!library.playing || reduced)) { timeline in
                let beat = library.playing && !reduced ? sin(timeline.date.timeIntervalSince(born)*3.2) : 0
                ZStack {
                    RoundedRectangle(cornerRadius:28).fill(accent.opacity(0.12))
                    Circle().fill(Color.sun.opacity(0.65)).frame(width:94,height:94).offset(x:100,y:-42)
                    Ellipse().fill(accent.opacity(0.2)).frame(width:260,height:70).offset(y:72)
                    ForEach(0..<5) { n in
                        Text(n % 2 == 0 ? "♪" : "♫").font(.system(size:CGFloat(22+n*3),weight:.bold))
                            .foregroundStyle(songColors[n].opacity(0.8))
                            .rotationEffect(.degrees(Double(n*12)-20+beat*5))
                            .offset(x:CGFloat(n%2 == 0 ? -1 : 1)*CGFloat(85+n*9),y:CGFloat(-65+n*26)+beat*6)
                    }
                    if reduced { Art(name:"navProfile",size:155) }
                    else { LobaView(mood:library.playing ? .cheer : .happy,size:170,tappable:false)
                        .rotationEffect(.degrees(beat*5),anchor:.bottom).offset(y:beat * -5) }
                    VStack { Spacer(); HStack(spacing:5) {
                        ForEach(0..<11) { n in RoundedRectangle(cornerRadius:3).fill(accent.opacity(0.7)).frame(width:5,height:CGFloat(8+(library.playing ? abs(sin(Double(n)+beat))*14 : 2))) }
                    }.frame(height:26).padding(.bottom,6) }
                }.frame(height:205).accessibilityHidden(true)
            }
            VStack(spacing:15) {
                Text(library.selected == nil ? L("Un peu de musique, beaucoup de joie.", "Little songs. Big smiles.") : library.selected!.title)
                    .font(.display(25)).foregroundStyle(Color.ink).multilineTextAlignment(.center).accessibilityAddTraits(.isHeader)
                if library.selected != nil {
                    VStack(spacing:3) {
                        Slider(value:Binding(get:{library.elapsed},set:{library.seek($0)}),in:0...max(1,library.duration)).tint(accent)
                            .disabled(library.duration <= 0).accessibilityLabel(L("Position dans la chanson", "Song position"))
                        HStack { Text(SongLibrary.clock(library.elapsed)); Spacer(); Text(SongLibrary.clock(library.duration)) }.font(.text(12)).monospacedDigit().foregroundStyle(Color.ink2)
                    }
                    HStack(spacing:0) {
                        roundButton(library.repeatSong ? "repeat.1" : "repeat",L("Répéter la chanson", "Repeat song"),selected:library.repeatSong) { library.repeatSong.toggle() }
                        Spacer(minLength:0)
                        roundButton("backward.end.fill",L("Chanson précédente", "Previous song")) { library.next(-1) }
                        Spacer(minLength:0)
                        Button { library.toggle() } label: {
                            ZStack {
                                Circle().fill(accent).shadow(color:accent.opacity(0.3),radius:0,x:0,y:5)
                                if library.buffering { ProgressView().tint(.white) }
                                else { Image(systemName:library.playing ? "pause.fill" : "play.fill").font(.system(size:25,weight:.bold)).foregroundStyle(.white) }
                            }.frame(width:64,height:64)
                        }.buttonStyle(.plain).accessibilityLabel(library.playing ? L("Pause", "Pause") : L("Écouter", "Play"))
                        Spacer(minLength:0)
                        roundButton("forward.end.fill",L("Chanson suivante", "Next song")) { library.next() }
                        Spacer(minLength:0)
                        roundButton("shuffle",L("Surprends-moi", "Surprise me")) { library.surprise() }
                    }
                } else {
                    Text(L("Des chansons d’enfance à partager, encore et encore.", "Childhood favorites to share, again and again.")).font(.text(15)).foregroundStyle(Color.ink2).multilineTextAlignment(.center)
                    Button { library.surprise() } label: { Label(L("Surprends-moi !", "Surprise me!"),systemImage:"shuffle") }.buttonStyle(.chunky(.sun)).disabled(library.songs.isEmpty)
                }
            }.padding(16)
        }.background(.white).clipShape(RoundedRectangle(cornerRadius:28))
            .overlay(RoundedRectangle(cornerRadius:28).stroke(Color.line,lineWidth:2))
            .shadow(color:Color.line,radius:0,x:0,y:5)
    }
    private func songCard(_ song:LibrarySong) -> some View {
        let active = library.selected?.id == song.id
        return VStack(alignment:.leading,spacing:10) {
            Button { library.play(song) } label: {
                SongSleeve(color:songColors[song.artwork],symbol:active && library.playing ? "waveform" : "music.note")
                    .frame(height:135).overlay(alignment:.bottomTrailing) {
                        Image(systemName:active && library.playing ? "speaker.wave.2.fill" : "play.fill")
                            .font(.system(size:15,weight:.bold)).foregroundStyle(Color.ink).padding(12).background(.white,in:Circle()).padding(10)
                    }
            }.buttonStyle(.plain).accessibilityLabel(L("Écouter", "Play") + " " + song.title)
            HStack(alignment:.top,spacing:3) {
                Text(song.title).font(.text(17,.heavy)).foregroundStyle(Color.ink).fixedSize(horizontal:false,vertical:true).frame(maxWidth:.infinity,alignment:.leading)
                Button { library.favorite(song) } label: {
                    Image(systemName:library.favorites.contains(song.id) ? "heart.fill" : "heart").font(.system(size:20,weight:.semibold)).foregroundStyle(Color.coral).frame(width:44,height:44)
                }.buttonStyle(.plain).accessibilityLabel((library.favorites.contains(song.id) ? L("Retirer des favoris", "Remove favorite") : L("Ajouter aux favoris", "Add favorite")) + " " + song.title)
            }.padding(.horizontal,4)
        }.padding(10).background(.white,in:RoundedRectangle(cornerRadius:24))
            .overlay(RoundedRectangle(cornerRadius:24).stroke(active ? songColors[song.artwork] : Color.line,lineWidth:2))
            .shadow(color:Color.line,radius:0,x:0,y:4)
    }
    private func filter(_ title:String,active:Bool,action:@escaping ()->Void) -> some View {
        Button(action:action) { Text(title).font(.text(14,.heavy)).padding(.horizontal,14).frame(minHeight:44).foregroundStyle(active ? Color.white : Color.ink2).background(active ? Color.ink : Color.white,in:Capsule()) }.buttonStyle(.plain).accessibilityAddTraits(active ? .isSelected : [])
    }
    private func roundButton(_ icon:String,_ label:String,selected:Bool=false,action:@escaping ()->Void) -> some View {
        Button(action:action) { Image(systemName:icon).font(.system(size:18,weight:.bold)).foregroundStyle(selected ? Color.plum : Color.ink).frame(width:44,height:44).background(selected ? Color.plum.opacity(0.14) : Color.surface,in:Circle()) }.buttonStyle(.plain).accessibilityLabel(label).accessibilityAddTraits(selected ? .isSelected : [])
    }
}
