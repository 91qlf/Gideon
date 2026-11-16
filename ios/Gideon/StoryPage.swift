import SwiftUI

/// One full-screen story: photo (slow Ken Burns zoom) or video, headline, outlet, glass action rail.
struct StoryPage: View {
    let story: Story
    let mode: PageMode
    let insets: EdgeInsets
    let onOpen: (Story) -> Void

    @Environment(NewsStore.self) private var store
    @State private var userPaused = false
    @State private var started = false
    @State private var ended = false
    @State private var failed = false
    @State private var replayToken = 0
    @State private var burst = 0
    @State private var burstPoint = CGPoint.zero

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            ZStack(alignment: .bottom) {
                Color.black
                if story.isVideo {
                    videoLayer(size)
                } else {
                    heroLayer(size)
                }
                scrim
                overlayContent
                if burst > 0 {
                    HeartBurst()
                        .id(burst)
                        .position(burstPoint)
                }
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .onTapGesture(count: 2) { location in
                burstPoint = location
                burst += 1
                store.toggleLike(story, forceOn: true)
            }
            .onTapGesture {
                primaryTap()
            }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: burst)
        .onChange(of: mode) { _, newMode in
            switch newMode {
            case .idle:
                started = false
                ended = false
                failed = false
                userPaused = false
            case .active:
                userPaused = false
            case .preload:
                break
            }
        }
    }

    private func primaryTap() {
        guard story.isVideo else {
            onOpen(story)
            return
        }
        if failed {
            if let url = story.watchURL { UIApplication.shared.open(url) }
        } else if ended {
            replay()
        } else {
            userPaused.toggle()
        }
    }

    private func replay() {
        ended = false
        userPaused = false
        replayToken += 1
    }

    private func handle(_ event: YouTubeView.Event) {
        switch event {
        case .playing:
            ended = false
            withAnimation(.easeOut(duration: 0.25)) { started = true }
        case .ended:
            ended = true
        case .failed:
            failed = true
        case .ready, .paused:
            break
        }
    }

    // MARK: - Media

    private func heroLayer(_ size: CGSize) -> some View {
        let height = size.height * 0.64
        return VStack(spacing: 0) {
            ZStack {
                if let url = story.imageURL {
                    AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.35))) { phase in
                        switch phase {
                        case .success(let image):
                            image.resizable().scaledToFill()
                        case .failure:
                            fallbackArt
                        default:
                            Color(white: 0.1)
                        }
                    }
                } else {
                    fallbackArt
                }
            }
            .frame(width: size.width, height: height)
            .scaleEffect(mode == .active ? 1.12 : 1.0)
            .animation(mode == .active ? Animation.linear(duration: 20) : Animation.easeOut(duration: 0.4),
                       value: mode == .active)
            .frame(width: size.width, height: height)
            .clipped()
            Spacer(minLength: 0)
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }

    private var fallbackArt: some View {
        ZStack {
            LinearGradient(colors: [story.category.color.opacity(0.85), story.category.color.opacity(0.25), .black],
                           startPoint: .topLeading, endPoint: .bottom)
            Image(systemName: story.category.symbol)
                .font(.system(size: 120, weight: .bold))
                .foregroundStyle(.white.opacity(0.14))
                .offset(x: 70, y: -30)
            LogoView(publisher: store.publisher(of: story), size: 76, corner: 18)
                .shadow(color: .black.opacity(0.3), radius: 16, y: 6)
        }
    }

    private func videoLayer(_ size: CGSize) -> some View {
        let frame = story.isShort
            ? CGSize(width: size.width, height: min(size.height, size.width * 16 / 9))
            : CGSize(width: size.width, height: size.width * 9 / 16)
        let centerY = story.isShort ? size.height * 0.46 : size.height * 0.38
        return ZStack {
            AsyncImage(url: story.imageURL) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Color.black
            }
            .frame(width: size.width, height: size.height)
            .blur(radius: 40)
            .opacity(0.55)
            .clipped()

            ZStack {
                if let id = story.v, mode != .idle {
                    YouTubeView(videoID: id,
                                muted: !store.soundOn || mode != .active,
                                playing: mode == .active && !userPaused && !ended,
                                replayToken: replayToken) { event in
                        handle(event)
                    }
                }
                if !started || mode == .idle {
                    AsyncImage(url: story.imageURL) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Color.black
                    }
                    .frame(width: frame.width, height: frame.height)
                    .clipped()
                    .transition(.opacity)
                }
                if mode == .active && !started && !failed {
                    ProgressView().tint(.white)
                }
                if userPaused && started && !ended && !failed {
                    Image(systemName: "play.fill")
                        .font(.system(size: 54))
                        .foregroundStyle(.white.opacity(0.9))
                        .shadow(color: .black.opacity(0.4), radius: 10)
                        .transition(.scale.combined(with: .opacity))
                }
                if ended {
                    Button(action: replay) {
                        Label("Revoir", systemImage: "arrow.counterclockwise")
                            .font(.headline)
                            .padding(.horizontal, 20)
                            .frame(height: 46)
                            .contentShape(Capsule())
                            .glassCapsule(interactive: true)
                    }
                    .buttonStyle(.plain)
                }
                if failed {
                    Button {
                        if let url = story.watchURL { UIApplication.shared.open(url) }
                    } label: {
                        Label("Regarder sur YouTube", systemImage: "play.rectangle.fill")
                            .font(.headline)
                            .padding(.horizontal, 20)
                            .frame(height: 46)
                            .contentShape(Capsule())
                            .glassCapsule(interactive: true)
                    }
                    .buttonStyle(.plain)
                }
            }
            .foregroundStyle(.white)
            .frame(width: frame.width, height: frame.height)
            .clipped()
            .position(x: size.width / 2, y: centerY)
            .animation(.easeOut(duration: 0.2), value: userPaused)
        }
        .frame(width: size.width, height: size.height)
    }

    private var scrim: some View {
        LinearGradient(stops: [
            .init(color: .black.opacity(0.55), location: 0),
            .init(color: .clear, location: 0.16),
            .init(color: .clear, location: story.isVideo ? 0.56 : 0.4),
            .init(color: .black.opacity(0.85), location: story.isVideo ? 0.8 : 0.62),
            .init(color: .black, location: 1)
        ], startPoint: .top, endPoint: .bottom)
        .allowsHitTesting(false)
    }

    // MARK: - Text and actions

    private var overlayContent: some View {
        let pub = store.publisher(of: story)
        let liked = store.liked.contains(story.id)
        let saved = store.isSaved(story)
        return HStack(alignment: .bottom, spacing: 14) {
            VStack(alignment: .leading, spacing: 9) {
                Button {
                    store.setFilter(.publisher(story.p))
                } label: {
                    HStack(spacing: 8) {
                        LogoView(publisher: pub, size: 26)
                        Text(pub.name)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Text(timeAgo(story.date))
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.65))
                            .lineLimit(1)
                    }
                }
                .buttonStyle(.plain)

                HStack(spacing: 5) {
                    Image(systemName: story.isVideo ? "play.rectangle.fill" : story.category.symbol)
                    Text(story.isVideo ? (story.isShort ? "SHORT" : "VIDÉO") : story.category.label.uppercased())
                        .tracking(0.6)
                }
                .font(.caption2.weight(.bold))
                .foregroundStyle(story.isVideo ? Color.red : story.category.color)

                Text(story.title)
                    .font(.system(size: story.isVideo ? 19 : 23, weight: .bold))
                    .lineLimit(story.isVideo ? 3 : 4)
                    .shadow(color: .black.opacity(0.35), radius: 6, y: 1)

                if !story.isVideo && !story.summary.isEmpty {
                    Text(story.summary)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.82))
                        .lineLimit(3)
                }

                if !story.isVideo {
                    Button {
                        onOpen(story)
                    } label: {
                        HStack(spacing: 7) {
                            Image(systemName: "doc.text.fill")
                            Text(story.hasArticle ? "Lire l'article" : "Ouvrir l'article")
                            if let minutes = story.minutes {
                                Text("\(minutes) min")
                                    .foregroundStyle(.white.opacity(0.6))
                            }
                        }
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 16)
                        .frame(height: 40)
                        .contentShape(Capsule())
                        .glassCapsule(interactive: true)
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(spacing: 14) {
                RailButton(symbol: liked ? "heart.fill" : "heart", tint: liked ? .pink : .white, active: liked) {
                    store.toggleLike(story)
                }
                RailButton(symbol: saved ? "bookmark.fill" : "bookmark", tint: saved ? .yellow : .white, active: saved) {
                    store.toggleSave(story)
                }
                if let url = story.linkURL {
                    ShareLink(item: url, subject: Text(story.title)) {
                        RailIcon(symbol: "square.and.arrow.up")
                    }
                    .buttonStyle(.plain)
                }
                if story.isVideo {
                    RailButton(symbol: store.soundOn ? "speaker.wave.2.fill" : "speaker.slash.fill",
                               active: store.soundOn) {
                        store.setSound(!store.soundOn)
                    }
                }
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 16)
        .padding(.bottom, insets.bottom + 16)
    }
}

struct RailIcon: View {
    let symbol: String
    var tint: Color = .white

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 19, weight: .semibold))
            .foregroundStyle(tint)
            .contentTransition(.symbolEffect(.replace))
            .frame(width: 46, height: 46)
            .contentShape(Circle())
            .glassCircle()
    }
}

struct RailButton: View {
    let symbol: String
    var tint: Color = .white
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            RailIcon(symbol: symbol, tint: tint)
                .symbolEffect(.bounce, value: active)
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.impact(weight: .light), trigger: active)
    }
}
