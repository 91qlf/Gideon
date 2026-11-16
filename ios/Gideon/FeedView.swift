import SwiftUI

enum PageMode: Equatable {
    case active, preload, idle
}

/// The vertical, full-screen news feed (one story per page, swipe up for the next).
struct FeedView: View {
    @Environment(NewsStore.self) private var store
    let isVisible: Bool
    let onOpen: (Story) -> Void
    @State private var currentID: String?

    var body: some View {
        let feed = store.currentFeed
        let activeID = currentID ?? feed.first?.id
        let activeIndex = feed.firstIndex { $0.id == activeID } ?? 0

        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            ZStack(alignment: .top) {
                Color.black.ignoresSafeArea()
                if feed.isEmpty {
                    emptyState
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView(.vertical) {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(feed.enumerated()), id: \.element.id) { index, story in
                                StoryPage(story: story, mode: mode(for: index, active: activeIndex),
                                          insets: insets, onOpen: onOpen)
                                    .containerRelativeFrame([.horizontal, .vertical])
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .scrollTargetBehavior(.paging)
                    .scrollPosition(id: $currentID)
                    .scrollIndicators(.hidden)
                    .refreshable { await store.refresh(manual: true) }
                    .ignoresSafeArea()
                    .id(store.feedVersion)
                }
                FeedHeader()
            }
        }
        .environment(\.colorScheme, .dark)
        .onChange(of: store.feedVersion) { currentID = nil }
        .task(id: activeID) { await prefetch(after: activeIndex, in: feed) }
    }

    private func mode(for index: Int, active: Int) -> PageMode {
        if index == active { return isVisible ? .active : .preload }
        if index == active + 1 { return .preload }
        return .idle
    }

    /// Warms the image cache for the next stories so swiping feels instant.
    private func prefetch(after index: Int, in feed: [Story]) async {
        let urls = feed.dropFirst(index + 1).prefix(3).compactMap(\.imageURL)
        for url in urls {
            if Task.isCancelled { return }
            _ = try? await URLSession.shared.data(from: url)
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        if store.isLoading || (store.data == nil && store.message == nil) {
            VStack(spacing: 14) {
                ProgressView()
                    .controlSize(.large)
                    .tint(.white)
                Text("Chargement des infos…")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.7))
            }
        } else {
            ContentUnavailableView {
                Label("Aucune info", systemImage: "newspaper")
            } description: {
                Text(store.message ?? "Rien à afficher pour l'instant.")
            } actions: {
                Button("Réessayer") {
                    Task { await store.refresh(manual: true) }
                }
                .prominentButton()
            }
        }
    }
}

/// Floating glass header: Pour toi / Vidéos / Articles, active filter, refresh.
struct FeedHeader: View {
    @Environment(NewsStore.self) private var store

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                if let label = store.filterLabel {
                    Button {
                        store.setFilter(nil)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "chevron.left")
                                .font(.footnote.weight(.bold))
                            Text(label)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 38)
                        .contentShape(Capsule())
                        .glassCapsule(interactive: true)
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, 50)
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    FeedTabPicker(selected: store.feedTab) { store.setFeedTab($0) }
                }
                HStack {
                    Spacer()
                    Button {
                        Task { await store.refresh(manual: true) }
                    } label: {
                        ZStack {
                            if store.isLoading {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 15, weight: .semibold))
                            }
                        }
                        .frame(width: 38, height: 38)
                        .contentShape(Circle())
                        .glassCircle()
                    }
                    .buttonStyle(.plain)
                    .disabled(store.isLoading)
                    .accessibilityLabel("Actualiser")
                }
            }
            if let message = store.message {
                Text(message)
                    .font(.footnote.weight(.semibold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .glassCapsule()
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: message) {
                        try? await Task.sleep(for: .seconds(2.2))
                        withAnimation { store.message = nil }
                    }
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: store.message)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: store.filterLabel)
    }
}

/// Segmented control in a Liquid Glass capsule, the selection pill slides between tabs.
struct FeedTabPicker: View {
    let selected: FeedTab
    let onSelect: (FeedTab) -> Void
    @Namespace private var pill

    var body: some View {
        HStack(spacing: 0) {
            ForEach(FeedTab.allCases) { tab in
                Button {
                    onSelect(tab)
                } label: {
                    Text(tab.rawValue)
                        .font(.subheadline.weight(tab == selected ? .bold : .medium))
                        .foregroundStyle(tab == selected ? Color.white : Color.white.opacity(0.65))
                        .padding(.horizontal, 13)
                        .frame(height: 30)
                        .background {
                            if tab == selected {
                                Capsule()
                                    .fill(Color.white.opacity(0.2))
                                    .matchedGeometryEffect(id: "pill", in: pill)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .glassCapsule()
        .animation(.spring(response: 0.32, dampingFraction: 0.78), value: selected)
        .sensoryFeedback(.selection, trigger: selected)
    }
}
