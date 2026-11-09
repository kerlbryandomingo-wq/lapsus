import Foundation
import SwiftData
import SophiaKit

/// One visited page. Stored only on this device.
@Model
final class HistoryEntry {
    var url: String
    var title: String
    var visitedAt: Date

    init(url: String, title: String, visitedAt: Date = .now) {
        self.url = url
        self.title = title
        self.visitedAt = visitedAt
    }
}

/// A Study Assistant answer the student chose to keep.
@Model
final class SavedNote {
    var title: String
    var body: String
    var mode: String
    var createdAt: Date

    init(title: String, body: String, mode: String, createdAt: Date = .now) {
        self.title = title
        self.body = body
        self.mode = mode
        self.createdAt = createdAt
    }
}

enum SophiaStore {
    static let schema = Schema([HistoryEntry.self, SavedNote.self])

    static func makeContainer(inMemory: Bool) -> ModelContainer {
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
        if let container = try? ModelContainer(for: schema, configurations: [config]) { return container }
        // A damaged store must not stop the browser from opening: fall back to memory.
        return try! ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)])
    }
}

/// Writes and prunes browsing history.
@MainActor
struct HistoryRecorder {
    let context: ModelContext

    func record(url: String, title: String, now: Date = .now) {
        guard UrlPolicy.webURL(url) != nil else { return }
        var latest = FetchDescriptor<HistoryEntry>(sortBy: [SortDescriptor(\.visitedAt, order: .reverse)])
        latest.fetchLimit = 1
        if let last = try? context.fetch(latest).first, last.url == url {
            // A reload or an in-page update of the same address: refresh instead of duplicating.
            last.visitedAt = now
            if !title.isEmpty { last.title = title }
        } else {
            context.insert(HistoryEntry(url: url, title: title, visitedAt: now))
        }
        prune()
        try? context.save()
    }

    func updateTitle(url: String, title: String) {
        guard !title.isEmpty else { return }
        var latest = FetchDescriptor<HistoryEntry>(sortBy: [SortDescriptor(\.visitedAt, order: .reverse)])
        latest.fetchLimit = 1
        if let last = try? context.fetch(latest).first, last.url == url, last.title != title {
            last.title = title
            try? context.save()
        }
    }

    func prune(limit: Int = SophiaConstants.maxHistoryEntries) {
        let count = (try? context.fetchCount(FetchDescriptor<HistoryEntry>())) ?? 0
        guard count > limit else { return }
        var oldest = FetchDescriptor<HistoryEntry>(sortBy: [SortDescriptor(\.visitedAt, order: .forward)])
        oldest.fetchLimit = count - limit
        for e in (try? context.fetch(oldest)) ?? [] { context.delete(e) }
    }

    /// Deletes entries newer than `since`, or everything when nil.
    func clear(since: Date? = nil) {
        if let since {
            try? context.delete(model: HistoryEntry.self, where: #Predicate { $0.visitedAt >= since })
        } else {
            try? context.delete(model: HistoryEntry.self)
        }
        try? context.save()
    }
}
