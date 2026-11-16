import SwiftUI

@main
struct GideonApp: App {
    @State private var store = NewsStore()

    init() {
        URLCache.shared = URLCache(memoryCapacity: 64 * 1024 * 1024, diskCapacity: 300 * 1024 * 1024, directory: nil)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
        }
    }
}

enum AppTab: Hashable {
    case feed, saved, settings, search
}

struct RootView: View {
    @Environment(NewsStore.self) private var store
    @Environment(\.scenePhase) private var scenePhase
    @State private var selection: AppTab = .feed
    @State private var reading: Story?

    var body: some View {
        TabView(selection: $selection) {
            Tab("À la une", systemImage: "house.fill", value: AppTab.feed) {
                FeedView(isVisible: selection == .feed && reading == nil && scenePhase == .active, onOpen: open)
            }
            Tab("Gardés", systemImage: "bookmark.fill", value: AppTab.saved) {
                SavedView(onOpen: open)
            }
            Tab("Réglages", systemImage: "gearshape.fill", value: AppTab.settings) {
                SettingsView()
            }
            Tab(value: AppTab.search, role: .search) {
                SearchView(onOpen: open) { filter in
                    store.setFilter(filter)
                    selection = .feed
                }
            }
        }
        .tint(.pink)
        .minimizeTabBarOnScroll()
        .preferredColorScheme(selection == .feed ? ColorScheme.dark : nil)
        .sheet(item: $reading) { story in
            ReaderView(story: story)
        }
        .task {
            await store.refresh()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await store.refresh() }
            }
        }
    }

    private func open(_ story: Story) {
        store.noteOpened(story)
        reading = story
    }
}
