import SwiftUI

/// Search tab: keyboard opens straight away; sections, outlets and latest news while the field is empty.
struct SearchView: View {
    @Environment(NewsStore.self) private var store
    let onOpen: (Story) -> Void
    let onFilter: (StoryFilter) -> Void
    @State private var query = ""
    @FocusState private var searchFocused: Bool

    private var trimmed: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            List {
                if trimmed.isEmpty {
                    browse
                } else {
                    results
                }
            }
            .listStyle(.insetGrouped)
            .scrollDismissesKeyboard(.immediately)
            .navigationTitle("Recherche")
        }
        .searchable(text: $query, prompt: "Infos, journaux, sujets")
        .searchFocused($searchFocused)
        .task {
            try? await Task.sleep(for: .milliseconds(250))
            searchFocused = true
        }
    }

    // MARK: - Browse

    @ViewBuilder
    private var browse: some View {
        Section {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(NewsCategory.allCases) { category in
                    Button {
                        onFilter(.category(category))
                    } label: {
                        CategoryTile(category: category)
                    }
                    .buttonStyle(.plain)
                }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        } header: {
            Text("Rubriques")
        }

        outletStrip(title: "Journaux", kind: "rss")
        outletStrip(title: "Chaînes vidéo", kind: "youtube")

        Section {
            ForEach(latest) { story in
                Button {
                    onOpen(story)
                } label: {
                    StoryRow(story: story)
                }
                .buttonStyle(.plain)
            }
        } header: {
            Text("Dernières infos")
        }
    }

    private var latest: [Story] {
        Array(store.all.sorted { $0.d > $1.d }.prefix(20))
    }

    @ViewBuilder
    private func outletStrip(title: String, kind: String) -> some View {
        let outlets = store.publishers(kind: kind)
        if !outlets.isEmpty {
            Section {
                ScrollView(.horizontal) {
                    LazyHStack(alignment: .top, spacing: 14) {
                        ForEach(outlets) { pub in
                            Button {
                                onFilter(.publisher(pub.id))
                            } label: {
                                VStack(spacing: 6) {
                                    LogoView(publisher: pub, size: 56)
                                        .overlay(Circle().stroke(Color.primary.opacity(0.08), lineWidth: 1))
                                    Text(pub.name)
                                        .font(.caption2)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.center)
                                        .frame(width: 68)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .scrollIndicators(.hidden)
                .listRowInsets(EdgeInsets())
            } header: {
                Text(title)
            }
        }
    }

    // MARK: - Results

    @ViewBuilder
    private var results: some View {
        let needle = fold(trimmed)
        let outlets = (store.data?.publishers ?? []).filter { fold($0.name).contains(needle) }
        let hits = Array(store.all.filter { fold($0.title).contains(needle) || fold($0.summary).contains(needle) }.prefix(80))
        let header: String = hits.isEmpty ? "Aucune info trouvée" : (hits.count == 1 ? "1 info" : "\(hits.count) infos")

        if !outlets.isEmpty {
            Section {
                ForEach(outlets) { pub in
                    Button {
                        onFilter(.publisher(pub.id))
                    } label: {
                        HStack(spacing: 12) {
                            LogoView(publisher: pub, size: 30, corner: 7)
                            Text(pub.name)
                                .foregroundStyle(.primary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(.tertiary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            } header: {
                Text("Sources")
            }
        }

        Section {
            ForEach(hits) { story in
                Button {
                    onOpen(story)
                } label: {
                    StoryRow(story: story)
                }
                .buttonStyle(.plain)
            }
        } header: {
            Text(header)
        }
    }

    private func fold(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "fr_FR"))
    }
}

struct CategoryTile: View {
    let category: NewsCategory

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: category.symbol)
                .font(.title3.weight(.semibold))
            Text(category.label)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, minHeight: 62, alignment: .leading)
        .background(
            LinearGradient(colors: [category.color, category.color.opacity(0.72)],
                           startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 16, style: .continuous)
        )
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
