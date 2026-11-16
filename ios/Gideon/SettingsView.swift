import SwiftUI

struct SettingsView: View {
    @Environment(NewsStore.self) private var store

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 14) {
                        Image("Logo")
                            .resizable()
                            .frame(width: 60, height: 60)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Gideon")
                                .font(.title3.weight(.semibold))
                            Text("L'actu en vertical, mise à jour toute la journée.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }

                Section {
                    Toggle(isOn: Binding(get: { store.soundOn }, set: { store.setSound($0) })) {
                        Label {
                            Text("Son des vidéos")
                        } icon: {
                            SettingsIcon("speaker.wave.2.fill", .pink)
                        }
                    }
                    Toggle(isOn: Binding(get: { store.includeShorts }, set: { store.setShorts($0) })) {
                        Label {
                            Text("YouTube Shorts")
                        } icon: {
                            SettingsIcon("play.rectangle.on.rectangle.fill", .red)
                        }
                    }
                    Picker(selection: Binding(get: { store.textScale }, set: { store.setTextScale($0) })) {
                        Text("Petit").tag(0.9)
                        Text("Normal").tag(1.0)
                        Text("Grand").tag(1.12)
                        Text("Très grand").tag(1.25)
                    } label: {
                        Label {
                            Text("Taille du texte")
                        } icon: {
                            SettingsIcon("textformat.size", .blue)
                        }
                    }
                } header: {
                    Text("Lecture")
                } footer: {
                    Text("Les Shorts sont masqués par défaut pour garder un fil d'infos.")
                }

                Section {
                    sourcesLink(kind: "rss", title: "Journaux", symbol: "newspaper.fill", color: .orange)
                    sourcesLink(kind: "youtube", title: "Chaînes vidéo", symbol: "play.tv.fill", color: .red)
                } header: {
                    Text("Sources")
                }

                Section {
                    LabeledContent("Dernière mise à jour", value: updatedText)
                    LabeledContent("Infos disponibles", value: "\(store.all.count)")
                    Button {
                        openInApp(NewsStore.base)
                    } label: {
                        Label {
                            Text("Gideon Web")
                                .foregroundStyle(.primary)
                        } icon: {
                            SettingsIcon("safari.fill", .teal)
                        }
                    }
                    LabeledContent("Version", value: version)
                } header: {
                    Text("Gideon")
                } footer: {
                    Text("Les infos viennent des flux publics des journaux et des chaînes YouTube, récupérés automatiquement toutes les 30 minutes.")
                }
            }
            .navigationTitle("Réglages")
        }
    }

    private func sourcesLink(kind: String, title: String, symbol: String, color: Color) -> some View {
        let outlets = store.publishers(kind: kind)
        let enabled = outlets.filter { store.isEnabled($0, kind: kind) }.count
        return NavigationLink {
            SourcesView(kind: kind, title: title)
        } label: {
            HStack {
                Label {
                    Text(title)
                } icon: {
                    SettingsIcon(symbol, color)
                }
                Spacer()
                Text("\(enabled)/\(outlets.count)")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var updatedText: String {
        guard let stamp = store.data?.updatedAt else { return "—" }
        return timeAgo(Date(timeIntervalSince1970: stamp))
    }

    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }
}

struct SourcesView: View {
    @Environment(NewsStore.self) private var store
    let kind: String
    let title: String

    var body: some View {
        List {
            Section {
                ForEach(store.publishers(kind: kind)) { pub in
                    Toggle(isOn: Binding(get: { store.isEnabled(pub, kind: kind) },
                                         set: { store.setEnabled(pub, kind: kind, $0) })) {
                        HStack(spacing: 12) {
                            LogoView(publisher: pub, size: 30, corner: 7)
                            Text(pub.name)
                        }
                    }
                }
            } footer: {
                Text("Désactive une source pour ne plus la voir dans ton fil.")
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
