import SwiftUI
import SafariServices

// MARK: - Liquid Glass: the real iOS 26 material when available, system blur before.

#if compiler(>=6.2)
extension View {
    @ViewBuilder
    func glassCapsule(interactive: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(Glass.regular.interactive(interactive), in: Capsule())
        } else {
            self.background(.ultraThinMaterial, in: Capsule())
        }
    }

    @ViewBuilder
    func glassCircle(interactive: Bool = true) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(Glass.regular.interactive(interactive), in: Circle())
        } else {
            self.background(.ultraThinMaterial, in: Circle())
        }
    }

    @ViewBuilder
    func prominentButton() -> some View {
        if #available(iOS 26.0, *) {
            self.buttonStyle(.glassProminent)
        } else {
            self.buttonStyle(.borderedProminent)
        }
    }

    @ViewBuilder
    func minimizeTabBarOnScroll() -> some View {
        if #available(iOS 26.0, *) {
            self.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}
#else
extension View {
    func glassCapsule(interactive: Bool = false) -> some View {
        background(.ultraThinMaterial, in: Capsule())
    }

    func glassCircle(interactive: Bool = true) -> some View {
        background(.ultraThinMaterial, in: Circle())
    }

    func prominentButton() -> some View {
        buttonStyle(.borderedProminent)
    }

    func minimizeTabBarOnScroll() -> some View {
        self
    }
}
#endif

extension NewsCategory {
    var color: Color {
        switch self {
        case .une: return .pink
        case .monde: return .blue
        case .france: return .indigo
        case .eco: return .green
        case .tech: return .purple
        case .sciences: return .teal
        case .sport: return .orange
        case .culture: return .red
        }
    }
}

/// Opens a page in Safari inside the app (SFSafariViewController), like Apple News does.
@MainActor
func openInApp(_ url: URL) {
    let scheme = url.scheme?.lowercased() ?? ""
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    guard scheme == "http" || scheme == "https", var top = scene?.keyWindow?.rootViewController else {
        UIApplication.shared.open(url)
        return
    }
    while let next = top.presentedViewController { top = next }
    let config = SFSafariViewController.Configuration()
    config.barCollapsingEnabled = true
    let safari = SFSafariViewController(url: url, configuration: config)
    safari.preferredControlTintColor = .systemPink
    safari.dismissButtonStyle = .close
    top.present(safari, animated: true)
}

/// Newspaper logo on a white tile (site icon), initial as fallback.
struct LogoView: View {
    let publisher: Publisher
    var size: CGFloat = 22
    var corner: CGFloat? = nil

    var body: some View {
        AsyncImage(url: publisher.logoURL) { phase in
            if let image = phase.image {
                image.resizable().scaledToFit().padding(size * 0.12)
            } else {
                Text(String(publisher.name.prefix(1)))
                    .font(.system(size: size * 0.45, weight: .bold))
                    .foregroundStyle(.gray)
            }
        }
        .frame(width: size, height: size)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: corner ?? size / 2, style: .continuous))
    }
}

/// Apple News style row: outlet, headline, time, thumbnail on the right.
struct StoryRow: View {
    let story: Story
    @Environment(NewsStore.self) private var store

    var body: some View {
        let pub = store.publisher(of: story)
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    LogoView(publisher: pub, size: 16, corner: 4)
                    Text(pub.name)
                        .font(.footnote.weight(.semibold))
                        .lineLimit(1)
                }
                Text(story.title)
                    .font(.headline)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
                Text(timeAgo(story.date))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Color.clear
                .frame(width: 84, height: 84)
                .overlay {
                    AsyncImage(url: story.imageURL ?? pub.logoURL) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Rectangle().fill(.quaternary)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    if story.isVideo {
                        Image(systemName: "play.fill")
                            .font(.footnote)
                            .foregroundStyle(.white)
                            .padding(8)
                            .background(.black.opacity(0.5), in: Circle())
                    }
                }
        }
        .contentShape(Rectangle())
    }
}

/// Heart that pops where you double-tapped.
struct HeartBurst: View {
    @State private var popped = false
    @State private var gone = false

    var body: some View {
        Image(systemName: "heart.fill")
            .font(.system(size: 92))
            .foregroundStyle(LinearGradient(colors: [.pink, .red], startPoint: .top, endPoint: .bottom))
            .shadow(color: .black.opacity(0.3), radius: 12, y: 4)
            .rotationEffect(.degrees(popped ? -10 : 0))
            .scaleEffect(gone ? 1.5 : (popped ? 1 : 0.2))
            .opacity(gone ? 0 : 1)
            .offset(y: gone ? -120 : 0)
            .allowsHitTesting(false)
            .onAppear {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.55)) { popped = true }
                withAnimation(.easeIn(duration: 0.45).delay(0.6)) { gone = true }
            }
    }
}

/// White symbol on a coloured rounded square, like the iOS Settings app.
struct SettingsIcon: View {
    let symbol: String
    let color: Color

    init(_ symbol: String, _ color: Color) {
        self.symbol = symbol
        self.color = color
    }

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 29, height: 29)
            .background(color.gradient, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
    }
}
