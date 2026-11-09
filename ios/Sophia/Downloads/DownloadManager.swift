import Foundation
import Observation
import WebKit
import SophiaKit

enum DownloadState: Equatable {
    case running
    case finished
    case failed(String)
    case cancelled
}

@Observable
final class DownloadItem: Identifiable {
    let id = UUID()
    var fileName: String
    var mimeType: String?
    var sourceURL: String
    var startedAt: Date
    var received: Int64 = 0
    var total: Int64 = -1
    var state: DownloadState = .running
    var fileURL: URL?
    @ObservationIgnored weak var download: WKDownload?
    @ObservationIgnored var progressObservation: NSKeyValueObservation?

    init(fileName: String, mimeType: String?, sourceURL: String, startedAt: Date = .now) {
        self.fileName = fileName
        self.mimeType = mimeType
        self.sourceURL = sourceURL
        self.startedAt = startedAt
    }

    var typeLabel: String { FileNames.typeLabel(fileName, mimeType: mimeType) }

    var fraction: Double? { total > 0 ? min(1, Double(received) / Double(total)) : nil }

    var sizeLabel: String {
        switch state {
        case .running: return Formatting.progress(received: received, total: total)
        default: return Formatting.bytes(total > 0 ? total : received)
        }
    }
}

/// Saved record of a finished download, so the list survives relaunches.
private struct DownloadRecord: Codable {
    var fileName: String
    var mimeType: String?
    var sourceURL: String
    var startedAt: Date
    var size: Int64
    var relativePath: String
}

/// Native downloads through WKDownload. Files go to Documents/Downloads, which the Files app shows
/// under On My iPhone > Sophia.
@MainActor
@Observable
final class DownloadManager: NSObject {
    private(set) var items: [DownloadItem] = []
    /// Set when a finished download should be previewed right away.
    var previewURL: URL?
    @ObservationIgnored var openWhenDone: () -> Bool = { false }
    @ObservationIgnored private let directory: URL
    @ObservationIgnored private let recordsURL: URL
    @ObservationIgnored private var byDownload: [ObjectIdentifier: DownloadItem] = [:]

    init(directory: URL? = nil) {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.directory = directory ?? docs.appendingPathComponent("Downloads", isDirectory: true)
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
        recordsURL = support.appendingPathComponent("downloads.json")
        super.init()
        try? FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
        load()
    }

    var activeCount: Int { items.filter { $0.state == .running }.count }

    func track(_ download: WKDownload, sourceURL: String?) {
        download.delegate = self
        let item = DownloadItem(fileName: "Download", mimeType: nil, sourceURL: sourceURL ?? download.originalRequest?.url?.absoluteString ?? "")
        item.download = download
        item.progressObservation = download.progress.observe(\.completedUnitCount, options: [.new]) { [weak item] progress, _ in
            let done = progress.completedUnitCount
            let total = progress.totalUnitCount
            Task { @MainActor in
                item?.received = done
                if total > 0 { item?.total = total }
            }
        }
        byDownload[ObjectIdentifier(download)] = item
        items.insert(item, at: 0)
    }

    func cancel(_ item: DownloadItem) {
        guard item.state == .running else { return }
        item.download?.cancel { _ in }
        item.state = .cancelled
        cleanup(item)
    }

    /// Removes the entry, and the file when `deleteFile` is set.
    func remove(_ item: DownloadItem, deleteFile: Bool) {
        if item.state == .running { cancel(item) }
        if deleteFile, let url = item.fileURL { try? FileManager.default.removeItem(at: url) }
        items.removeAll { $0.id == item.id }
        save()
    }

    func clearFinished() {
        items.removeAll { $0.state != .running }
        save()
    }

    private func cleanup(_ item: DownloadItem) {
        item.progressObservation = nil
        if let d = item.download { byDownload[ObjectIdentifier(d)] = nil }
        save()
    }

    private func existingNames() -> Set<String> {
        let files = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        return Set(files).union(items.filter { $0.state == .running }.map(\.fileName))
    }

    private func load() {
        guard let data = try? Data(contentsOf: recordsURL),
              let records = try? JSONDecoder().decode([DownloadRecord].self, from: data) else { return }
        items = records.compactMap { r in
            let url = directory.appendingPathComponent(r.relativePath)
            guard FileManager.default.fileExists(atPath: url.path) else { return nil }
            let item = DownloadItem(fileName: r.fileName, mimeType: r.mimeType, sourceURL: r.sourceURL, startedAt: r.startedAt)
            item.total = r.size
            item.received = r.size
            item.state = .finished
            item.fileURL = url
            return item
        }
    }

    private func save() {
        let records = items.compactMap { item -> DownloadRecord? in
            guard item.state == .finished, let url = item.fileURL else { return nil }
            return DownloadRecord(fileName: item.fileName, mimeType: item.mimeType, sourceURL: item.sourceURL,
                                  startedAt: item.startedAt, size: max(item.total, item.received), relativePath: url.lastPathComponent)
        }
        if let data = try? JSONEncoder().encode(records) {
            try? data.write(to: recordsURL, options: [.atomic, .completeFileProtection])
        }
    }
}

extension DownloadManager: WKDownloadDelegate {
    nonisolated func download(_ download: WKDownload, decideDestinationUsing response: URLResponse, suggestedFilename: String, completionHandler: @escaping (URL?) -> Void) {
        MainActor.assumeIsolated {
            guard let item = byDownload[ObjectIdentifier(download)] else { completionHandler(nil); return }
            let name = FileNames.unique(FileNames.sanitize(suggestedFilename), existing: existingNames())
            item.fileName = name
            item.mimeType = response.mimeType
            if response.expectedContentLength > 0 { item.total = response.expectedContentLength }
            let url = directory.appendingPathComponent(name)
            item.fileURL = url
            completionHandler(url)
        }
    }

    nonisolated func downloadDidFinish(_ download: WKDownload) {
        MainActor.assumeIsolated {
            guard let item = byDownload[ObjectIdentifier(download)] else { return }
            if let url = item.fileURL {
                try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: url.path)
                let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.int64Value
                if let size { item.received = size; item.total = size }
            }
            item.state = .finished
            cleanup(item)
            if openWhenDone() { previewURL = item.fileURL }
        }
    }

    nonisolated func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        MainActor.assumeIsolated {
            guard let item = byDownload[ObjectIdentifier(download)] else { return }
            if item.state != .cancelled {
                let ns = error as NSError
                item.state = .failed(ns.code == NSURLErrorNotConnectedToInternet ? "No internet connection." : "The download didn't finish.")
            }
            if let url = item.fileURL { try? FileManager.default.removeItem(at: url) }
            cleanup(item)
        }
    }
}
