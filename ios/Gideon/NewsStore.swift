import Foundation
import Observation

@MainActor
@Observable
final class NewsStore {
    static let base = URL(string: "https://91qlf.github.io/Gideon/")!

    private(set) var data: FeedData?
    private(set) var all: [Story] = []
    private(set) var forYou: [Story] = []
    private(set) var publishers: [String: Publisher] = [:]
    private(set) var kinds: [String: Set<String>] = [:]
    private(set) var isLoading = false
    private(set) var feedVersion = 0
    var message: String?

    private(set) var feedTab: FeedTab = .forYou
    private(set) var filter: StoryFilter?
    private(set) var liked: Set<String> = []
    private(set) var saved: [Story] = []
    private(set) var disabled: Set<String> = []
    private(set) var includeShorts = false
    private(set) var soundOn = true
    private(set) var textScale: Double = 1
    private var affinity: [String: Double] = [:]

    func setFeedTab(_ tab: FeedTab) {
        guard tab != feedTab || filter != nil else { return }
        feedTab = tab
        filter = nil
        feedVersion += 1
    }

    func setFilter(_ newFilter: StoryFilter?) {
        filter = newFilter
        feedVersion += 1
    }

    func setSound(_ on: Bool) {
        soundOn = on
        save(on, "sound")
    }

    func setShorts(_ on: Bool) {
        includeShorts = on
        save(on, "shorts")
        rebuild()
    }

    init() {
        liked = Set(load("liked") ?? [String]())
        saved = load("saved") ?? []
        disabled = Set(load("disabled") ?? [String]())
        includeShorts = load("shorts") ?? false
        soundOn = load("sound") ?? true
        textScale = load("scale") ?? 1
        affinity = load("affinity") ?? [:]
        if let cached = try? Data(contentsOf: cacheFile), let feed = try? JSONDecoder().decode(FeedData.self, from: cached) {
            apply(feed)
        }
    }

    // MARK: - Feed

    var currentFeed: [Story] {
        switch filter {
        case .publisher(let id): return all.filter { $0.p == id }
        case .category(let cat): return all.filter { $0.c == cat.rawValue || (cat == .une && $0.isVideo) }
        case nil:
            switch feedTab {
            case .forYou: return forYou
            case .videos: return all.filter(\.isVideo)
            case .articles: return all.filter { !$0.isVideo }
            }
        }
    }

    var filterLabel: String? {
        switch filter {
        case .publisher(let id): return publishers[id]?.name
        case .category(let cat): return cat.label
        case nil: return nil
        }
    }

    func refresh(manual: Bool = false) async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            var url = Self.base.appending(path: "data/feed.json")
            url.append(queryItems: [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970)))])
            let (bytes, _) = try await URLSession.shared.data(from: url)
            let feed = try JSONDecoder().decode(FeedData.self, from: bytes)
            let before = Set(data?.items.map(\.id) ?? [])
            try? bytes.write(to: cacheFile)
            apply(feed)
            if manual {
                let fresh = feed.items.filter { !before.contains($0.id) }.count
                message = before.isEmpty ? nil : (fresh > 0 ? "\(fresh) nouvelles infos" : "Tu es à jour")
            }
        } catch {
            if manual || data == nil { message = "Connexion impossible" }
        }
    }

    private func apply(_ feed: FeedData) {
        data = feed
        publishers = Dictionary(feed.publishers.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        var k: [String: Set<String>] = [:]
        for item in feed.items { k[item.p, default: []].insert(item.k) }
        kinds = k
        rebuild()
    }

    private func rebuild() {
        guard let data else { return }
        let newAll = data.items.filter { !disabled.contains($0.groupKey) && (includeShorts || !$0.isShort) }
        let newForYou = mix(newAll)
        let changed = newAll.map(\.id) != all.map(\.id) || newForYou.map(\.id) != forYou.map(\.id)
        all = newAll
        forYou = newForYou
        if changed { feedVersion += 1 }
    }

    /// "Pour toi": fresh news first, nudged by what you like, a video every third story.
    private func mix(_ items: [Story]) -> [Story] {
        let now = Date().timeIntervalSince1970
        let maxAffinity = max(1, affinity.values.max() ?? 1)
        func score(_ s: Story) -> Double {
            exp(-((now - s.d) / 3600) / 14) * 10 + (affinity[s.c] ?? 0) / maxAffinity * 3
                + (s.c == "UNE" ? 1.2 : 0) + (s.i != nil ? 2 : 0)
        }
        var articles = items.filter { !$0.isVideo }.sorted { score($0) > score($1) }
        var videos = items.filter(\.isVideo).sorted { score($0) > score($1) }
        var out: [Story] = []
        var sinceVideo = 0
        while !articles.isEmpty || !videos.isEmpty {
            let wantVideo = !videos.isEmpty && (sinceVideo >= 2 || articles.isEmpty)
            let last = out.last?.p
            if wantVideo {
                let idx = videos.prefix(4).firstIndex { $0.p != last } ?? 0
                out.append(videos.remove(at: idx))
                sinceVideo = 0
            } else {
                let idx = articles.prefix(4).firstIndex { $0.p != last } ?? 0
                out.append(articles.remove(at: idx))
                sinceVideo += 1
            }
        }
        return out
    }

    // MARK: - Articles

    func article(for story: Story) async -> Article? {
        guard story.hasArticle else { return nil }
        let url = Self.base.appending(path: "data/a/\(story.id.replacingOccurrences(of: ":", with: "_")).json")
        guard let (bytes, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSONDecoder().decode(Article.self, from: bytes)
    }

    // MARK: - User actions

    func publisher(of story: Story) -> Publisher {
        publishers[story.p] ?? Publisher(id: story.p, name: story.p, domain: "")
    }

    func isSaved(_ story: Story) -> Bool { saved.contains { $0.id == story.id } }

    func toggleLike(_ story: Story, forceOn: Bool = false) {
        if liked.contains(story.id) && !forceOn {
            liked.remove(story.id)
        } else if !liked.contains(story.id) {
            liked.insert(story.id)
            bump(story, 1)
        }
        save(Array(liked), "liked")
    }

    func toggleSave(_ story: Story) {
        if isSaved(story) {
            saved.removeAll { $0.id == story.id }
        } else {
            saved.insert(story, at: 0)
            bump(story, 1.5)
        }
        save(saved, "saved")
    }

    func removeSaved(at offsets: IndexSet) {
        for index in offsets.sorted(by: >) where saved.indices.contains(index) {
            saved.remove(at: index)
        }
        save(saved, "saved")
    }

    func noteOpened(_ story: Story) { bump(story, 0.5) }

    func isEnabled(_ publisher: Publisher, kind: String) -> Bool { !disabled.contains("\(kind):\(publisher.id)") }

    func setEnabled(_ publisher: Publisher, kind: String, _ on: Bool) {
        let key = "\(kind):\(publisher.id)"
        if on { disabled.remove(key) } else { disabled.insert(key) }
        save(Array(disabled), "disabled")
        rebuild()
    }

    func publishers(kind: String) -> [Publisher] {
        (data?.publishers ?? []).filter { kinds[$0.id]?.contains(kind) == true }
    }

    func setTextScale(_ scale: Double) {
        textScale = scale
        save(scale, "scale")
    }

    func cycleTextScale() {
        let steps: [Double] = [0.9, 1, 1.12, 1.25]
        let idx = steps.firstIndex { $0 >= textScale - 0.01 } ?? 1
        textScale = steps[(idx + 1) % steps.count]
        save(textScale, "scale")
    }

    private func bump(_ story: Story, _ by: Double) {
        affinity[story.c, default: 0] += by
        save(affinity, "affinity")
    }

    // MARK: - Persistence

    private var cacheFile: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appending(path: "feed.json")
    }

    private func save<T: Encodable>(_ value: T, _ key: String) {
        if let bytes = try? JSONEncoder().encode(value) { UserDefaults.standard.set(bytes, forKey: "gideon.\(key)") }
    }

    private func load<T: Decodable>(_ key: String) -> T? {
        guard let bytes = UserDefaults.standard.data(forKey: "gideon.\(key)") else { return nil }
        return try? JSONDecoder().decode(T.self, from: bytes)
    }
}
