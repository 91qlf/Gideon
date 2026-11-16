import Foundation

/// Data published every 30 minutes by the GitHub Action (same as Gideon Web).
struct FeedData: Codable {
    let updatedAt: Double
    let publishers: [Publisher]
    let items: [Story]
}

struct Publisher: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let domain: String

    var logoURL: URL? { URL(string: "https://www.google.com/s2/favicons?domain=\(domain)&sz=128") }
}

struct Story: Codable, Identifiable, Hashable {
    let id: String
    let t: String
    let s: String?
    let l: String
    let i: String?
    let p: String
    let c: String
    let k: String
    let d: Double
    let v: String?
    let sh: Bool?
    let a: Int?
    let m: Int?
    let lead: String?

    var title: String { t }
    var summary: String { lead ?? s ?? "" }
    var isVideo: Bool { !(v ?? "").isEmpty }
    var isShort: Bool { sh ?? false }
    var hasArticle: Bool { (a ?? 0) == 1 }
    var minutes: Int? { m }
    var imageURL: URL? { i.flatMap { URL(string: $0) } }
    var linkURL: URL? { URL(string: l) }
    var date: Date { Date(timeIntervalSince1970: d) }
    var category: NewsCategory { NewsCategory(rawValue: c) ?? .une }
    var groupKey: String { "\(k):\(p)" }
    var watchURL: URL? { v.flatMap { URL(string: "https://www.youtube.com/watch?v=\($0)") } }
}

enum NewsCategory: String, CaseIterable, Identifiable {
    case une = "UNE", monde = "MONDE", france = "FRANCE", eco = "ECO"
    case tech = "TECH", sciences = "SCIENCES", sport = "SPORT", culture = "CULTURE"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .une: return "À la une"
        case .monde: return "Monde"
        case .france: return "France"
        case .eco: return "Économie"
        case .tech: return "Tech"
        case .sciences: return "Sciences"
        case .sport: return "Sport"
        case .culture: return "Culture"
        }
    }

    var symbol: String {
        switch self {
        case .une: return "newspaper.fill"
        case .monde: return "globe.europe.africa.fill"
        case .france: return "flag.fill"
        case .eco: return "chart.line.uptrend.xyaxis"
        case .tech: return "cpu.fill"
        case .sciences: return "flask.fill"
        case .sport: return "soccerball"
        case .culture: return "theatermasks.fill"
        }
    }
}

struct Article: Codable {
    let title: String?
    let byline: String?
    let hero: String?
    let standfirst: String?
    let blocks: [ArticleBlock]
    let truncated: Bool
    let minutes: Int
}

struct ArticleBlock: Codable, Hashable {
    let t: String
    let x: String?
    let u: String?
}

enum FeedTab: String, CaseIterable, Identifiable {
    case forYou = "Pour toi"
    case videos = "Vidéos"
    case articles = "Articles"

    var id: String { rawValue }
}

enum StoryFilter: Equatable {
    case category(NewsCategory)
    case publisher(String)
}

func timeAgo(_ date: Date) -> String {
    let minutes = max(0, Int(Date().timeIntervalSince(date) / 60))
    if minutes < 1 { return "à l'instant" }
    if minutes < 60 { return "il y a \(minutes) min" }
    let hours = minutes / 60
    if hours < 24 { return "il y a \(hours) h" }
    let days = hours / 24
    return days == 1 ? "hier" : "il y a \(days) j"
}
