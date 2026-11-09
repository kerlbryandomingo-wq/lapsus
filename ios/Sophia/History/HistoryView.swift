import SwiftUI
import SwiftData
import SophiaKit

/// Browsing history: search, delete single pages, or clear a time range.
struct HistoryView: View {
    let onOpen: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var search = ""
    @State private var confirmClear = false

    var body: some View {
        HistoryList(search: search, onOpen: onOpen)
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search history")
            .navigationTitle("History")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .destructiveAction) {
                    Button("Clear", role: .destructive) { confirmClear = true }
                        .accessibilityIdentifier("history_clear")
                }
            }
            .confirmationDialog("Clear browsing history", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Last hour", role: .destructive) { recorder.clear(since: .now.addingTimeInterval(-3600)) }
                Button("Today", role: .destructive) { recorder.clear(since: Calendar.current.startOfDay(for: .now)) }
                Button("All history", role: .destructive) { recorder.clear() }
            } message: {
                Text("History is stored only on this device. Clearing it doesn't sign you out of any site.")
            }
    }

    private var recorder: HistoryRecorder { HistoryRecorder(context: context) }
}

private struct HistoryList: View {
    let onOpen: (String) -> Void
    @Query private var entries: [HistoryEntry]
    @Environment(\.modelContext) private var context

    init(search: String, onOpen: @escaping (String) -> Void) {
        self.onOpen = onOpen
        let q = search.trimmingCharacters(in: .whitespaces)
        let filter: Predicate<HistoryEntry>? = q.isEmpty ? nil : #Predicate { entry in
            entry.title.localizedStandardContains(q) || entry.url.localizedStandardContains(q)
        }
        var d = FetchDescriptor<HistoryEntry>(predicate: filter, sortBy: [SortDescriptor(\.visitedAt, order: .reverse)])
        d.fetchLimit = 1000
        _entries = Query(d)
    }

    var body: some View {
        let groups = Dictionary(grouping: entries) { Calendar.current.startOfDay(for: $0.visitedAt) }
        let days = groups.keys.sorted(by: >)
        List {
            if entries.isEmpty {
                ContentUnavailableView("No history", systemImage: "clock",
                                       description: Text("Pages you visit appear here. Turn history off in Settings > Browser."))
                    .listRowBackground(Color.clear)
            }
            ForEach(days, id: \.self) { day in
                Section(day.formatted(.dateTime.weekday(.wide).month().day().year())) {
                    ForEach(groups[day] ?? []) { e in
                        Button { onOpen(e.url) } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(e.title.isEmpty ? UrlPolicy.host(of: e.url) : e.title).font(.body).foregroundStyle(.primary).lineLimit(1)
                                Text(UrlPolicy.displayURL(e.url)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                Text(e.visitedAt.formatted(date: .omitted, time: .shortened)).font(.caption2).foregroundStyle(.tertiary)
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                context.delete(e)
                                try? context.save()
                            } label: { Label("Delete", systemImage: "trash") }
                        }
                    }
                }
            }
        }
        .accessibilityIdentifier("history_list")
    }
}
