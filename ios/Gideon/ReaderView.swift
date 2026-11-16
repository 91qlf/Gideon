import SwiftUI

/// The article, read inside Gideon: hero, serif headline, standfirst, text, quotes and photos.
struct ReaderView: View {
    let story: Story
    @Environment(NewsStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var article: Article?
    @State private var loading = true

    var body: some View {
        let pub = store.publisher(of: story)
        let scale = store.textScale
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    media
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(spacing: 8) {
                            LogoView(publisher: pub, size: 24, corner: 6)
                            Text(pub.name)
                                .font(.subheadline.weight(.semibold))
                            Spacer()
                            Text(story.isVideo ? "VIDÉO" : story.category.label.uppercased())
                                .font(.caption.weight(.bold))
                                .tracking(0.6)
                                .foregroundStyle(story.isVideo ? Color.red : story.category.color)
                        }
                        Text(article?.title ?? story.title)
                            .font(.system(size: 28 * scale, weight: .bold, design: .serif))
                            .fixedSize(horizontal: false, vertical: true)
                        Text(metaLine)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        if let standfirst {
                            Text(standfirst)
                                .font(.system(size: 19 * scale, weight: .medium))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Divider()
                            .padding(.vertical, 4)
                        content(pub: pub, scale: scale)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 48)
                    .textSelection(.enabled)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel("Fermer")
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if !story.isVideo {
                        Button {
                            store.cycleTextScale()
                        } label: {
                            Image(systemName: "textformat.size")
                        }
                        .accessibilityLabel("Taille du texte")
                    }
                    Button {
                        store.toggleSave(story)
                    } label: {
                        Image(systemName: store.isSaved(story) ? "bookmark.fill" : "bookmark")
                    }
                    .accessibilityLabel("Garder")
                    if let url = story.linkURL {
                        ShareLink(item: url) {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                }
            }
        }
        .tint(.pink)
        .presentationDragIndicator(.visible)
        .task {
            if story.hasArticle {
                article = await store.article(for: story)
            }
            loading = false
        }
    }

    private var standfirst: String? {
        if let text = article?.standfirst, !text.isEmpty { return text }
        if article == nil, !story.summary.isEmpty { return story.summary }
        return nil
    }

    private var metaLine: String {
        var parts: [String] = []
        if let byline = article?.byline, !byline.isEmpty { parts.append(byline) }
        parts.append(timeAgo(story.date))
        if let minutes = article?.minutes ?? story.minutes, !story.isVideo { parts.append("\(minutes) min de lecture") }
        return parts.joined(separator: " · ")
    }

    @ViewBuilder
    private var media: some View {
        if let id = story.v, story.isVideo {
            YouTubeView(videoID: id, muted: !store.soundOn, playing: true, interactive: true)
                .aspectRatio(story.isShort ? 9.0 / 16.0 : 16.0 / 9.0, contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: story.isShort ? 560 : nil)
                .background(Color.black)
        } else if let url = URL(string: article?.hero ?? "") ?? story.imageURL {
            Color.clear
                .frame(height: 260)
                .overlay {
                    AsyncImage(url: url, transaction: Transaction(animation: .easeOut(duration: 0.3))) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        } else {
                            Rectangle().fill(.quaternary)
                        }
                    }
                }
                .clipped()
        }
    }

    @ViewBuilder
    private func content(pub: Publisher, scale: Double) -> some View {
        if loading && story.hasArticle {
            HStack {
                Spacer()
                ProgressView()
                Spacer()
            }
            .padding(.vertical, 30)
        } else if let article, !article.blocks.isEmpty {
            ForEach(Array(article.blocks.enumerated()), id: \.offset) { _, block in
                BlockView(block: block, scale: scale, accent: story.category.color)
            }
            if article.truncated {
                continueButton("Lire la suite sur \(pub.name)")
            } else {
                Button {
                    if let url = story.linkURL { openInApp(url) }
                } label: {
                    Label("Article original sur \(pub.name)", systemImage: "safari")
                        .font(.footnote.weight(.semibold))
                }
                .padding(.top, 10)
            }
        } else if story.isVideo {
            Button {
                if let url = story.watchURL { UIApplication.shared.open(url) }
            } label: {
                Label("Regarder sur YouTube", systemImage: "play.rectangle.fill")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .prominentButton()
            .controlSize(.large)
        } else {
            continueButton("Lire l'article sur \(pub.name)")
        }
    }

    private func continueButton(_ title: String) -> some View {
        Button {
            if let url = story.linkURL { openInApp(url) }
        } label: {
            Label(title, systemImage: "safari")
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
        }
        .prominentButton()
        .controlSize(.large)
        .padding(.top, 8)
    }
}

struct BlockView: View {
    let block: ArticleBlock
    let scale: Double
    let accent: Color

    var body: some View {
        switch block.t {
        case "h":
            Text(block.x ?? "")
                .font(.system(size: 21 * scale, weight: .bold))
                .padding(.top, 8)
                .fixedSize(horizontal: false, vertical: true)
        case "q":
            HStack(alignment: .top, spacing: 12) {
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(accent)
                    .frame(width: 3)
                Text(block.x ?? "")
                    .font(.system(size: 19 * scale, design: .serif))
                    .italic()
            }
            .fixedSize(horizontal: false, vertical: true)
        case "li":
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text("•").foregroundStyle(accent)
                Text(block.x ?? "")
            }
            .font(.system(size: 17 * scale))
            .fixedSize(horizontal: false, vertical: true)
        case "img":
            if let raw = block.u, let url = URL(string: raw) {
                VStack(alignment: .leading, spacing: 6) {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(.quaternary)
                            .frame(height: 200)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    if let caption = block.x, !caption.isEmpty {
                        Text(caption)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }
        default:
            Text(block.x ?? "")
                .font(.system(size: 17 * scale))
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
