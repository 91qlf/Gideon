import SwiftUI

struct SavedView: View {
    @Environment(NewsStore.self) private var store
    let onOpen: (Story) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if store.saved.isEmpty {
                    ContentUnavailableView("Rien de gardé pour l'instant",
                                           systemImage: "bookmark",
                                           description: Text("Touche le signet d'une info pour la retrouver ici."))
                } else {
                    List {
                        ForEach(store.saved) { story in
                            Button {
                                onOpen(story)
                            } label: {
                                StoryRow(story: story)
                            }
                            .buttonStyle(.plain)
                        }
                        .onDelete { offsets in
                            store.removeSaved(at: offsets)
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Gardés")
            .toolbar {
                if !store.saved.isEmpty {
                    EditButton()
                }
            }
        }
    }
}
