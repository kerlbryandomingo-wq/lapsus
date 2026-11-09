import SwiftUI
import QuickLook
import SophiaKit

struct DownloadsView: View {
    let manager: DownloadManager
    @Environment(\.dismiss) private var dismiss
    @State private var preview: URL?
    @State private var confirmDelete: DownloadItem?

    var body: some View {
        List {
            if manager.items.isEmpty {
                ContentUnavailableView("No downloads", systemImage: "arrow.down.circle",
                                       description: Text("Files you download from Discipulus and other sites appear here and in the Files app, under On My iPhone > Sophia."))
                    .listRowBackground(Color.clear)
            }
            ForEach(manager.items) { item in
                DownloadRow(item: item, onCancel: { manager.cancel(item) }, onOpen: { preview = item.fileURL })
                    .swipeActions {
                        Button(role: .destructive) { confirmDelete = item } label: { Label("Delete", systemImage: "trash") }
                    }
                    .contextMenu {
                        if let url = item.fileURL, item.state == .finished {
                            Button { preview = url } label: { Label("Open", systemImage: "eye") }
                            ShareLink(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
                            Button { openInFiles(url) } label: { Label("Show in Files", systemImage: "folder") }
                        }
                        Button(role: .destructive) { confirmDelete = item } label: { Label("Delete", systemImage: "trash") }
                    }
            }
        }
        .navigationTitle("Downloads")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            ToolbarItem(placement: .bottomBar) {
                Button("Clear list") { manager.clearFinished() }
                    .disabled(manager.items.allSatisfy { $0.state == .running })
            }
        }
        .quickLookPreview($preview)
        .confirmationDialog("Delete \(confirmDelete?.fileName ?? "file")?", isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }),
                            titleVisibility: .visible, presenting: confirmDelete) { item in
            Button("Delete file", role: .destructive) { manager.remove(item, deleteFile: true) }
            Button("Remove from list only") { manager.remove(item, deleteFile: false) }
        }
    }

    private func openInFiles(_ url: URL) {
        // shareddocuments:// opens the Files app at this file's folder.
        var c = URLComponents(url: url.deletingLastPathComponent(), resolvingAgainstBaseURL: false)
        c?.scheme = "shareddocuments"
        if let target = c?.url { UIApplication.shared.open(target) }
    }
}

private struct DownloadRow: View {
    let item: DownloadItem
    let onCancel: () -> Void
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(SophiaColor.maroon)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 4) {
                Text(item.fileName).font(.body).lineLimit(2)
                Text("\(item.typeLabel) · \(item.sizeLabel)").font(.caption).foregroundStyle(.secondary)
                switch item.state {
                case .running:
                    if let f = item.fraction { ProgressView(value: f) } else { ProgressView().progressViewStyle(.linear) }
                case .failed(let message):
                    Text(message).font(.caption).foregroundStyle(.red)
                case .cancelled:
                    Text("Cancelled").font(.caption).foregroundStyle(.secondary)
                case .finished:
                    EmptyView()
                }
            }
            Spacer(minLength: 0)
            if item.state == .running {
                Button(action: onCancel) { Image(systemName: "xmark.circle.fill").font(.title2) }
                    .buttonStyle(.borderless)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Cancel download")
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if item.state == .finished { onOpen() } }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(item.state == .finished ? .isButton : [])
    }

    private var icon: String {
        switch item.typeLabel {
        case "PDF document": return "doc.richtext"
        case "Image": return "photo"
        case "Video": return "film"
        case "Audio": return "waveform"
        case "Archive": return "doc.zipper"
        case "Spreadsheet": return "tablecells"
        case "Presentation": return "rectangle.on.rectangle"
        default: return "doc"
        }
    }
}
