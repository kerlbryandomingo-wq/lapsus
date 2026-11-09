import SwiftUI
import SwiftData
import SophiaKit

/// Sophia's two side panels, layered over the page.
/// - Swipe from the left edge: address bar and bookmarks.
/// - Swipe from the right edge: Back, Forward, Reload and Settings.
/// - Swipe a panel back toward its edge, or tap the page beside it, to close it.
///
/// When both panels are closed this view draws nothing at all, so the page keeps its own colours.
/// The grey belongs only to the panel; the uncovered part of the page is never tinted or dimmed.
struct PanelOverlay: View {
    @Bindable var model: BrowserModel

    var body: some View {
        let address = model.reveal(.address)
        let controls = model.reveal(.controls)
        if address > 0 || controls > 0 {
            ZStack {
                // Catches taps beside the panel. Fully transparent: no grey, no dimming.
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture { model.close() }
                    .accessibilityElement()
                    .accessibilityLabel("Close panel")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { model.close() }
                    .accessibilityIdentifier("panel_dismiss")

                if address > 0 {
                    let w = model.panelWidth(.address)
                    AddressPanel(model: model)
                        .frame(width: w)
                        .frame(maxHeight: .infinity)
                        .background(SophiaColor.panel, ignoresSafeAreaEdges: .all)
                        .offset(x: -(1 - address) * w)
                        .simultaneousGesture(closeDrag(.left))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .transition(.identity)
                }
                if controls > 0 {
                    let w = model.panelWidth(.controls)
                    ControlsPanel(model: model)
                        .frame(width: w)
                        .frame(maxHeight: .infinity)
                        .background(SophiaColor.panel, ignoresSafeAreaEdges: .all)
                        .offset(x: (1 - controls) * w)
                        .simultaneousGesture(closeDrag(.right))
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .transition(.identity)
                }
            }
            .environment(\.colorScheme, .dark) // white content on the grey panel, in light and dark mode
            .onKeyPress(.escape) { model.close(); return .handled }
        }
    }

    /// Dragging an open panel back toward its edge closes it, following the finger.
    private func closeDrag(_ side: PanelSide) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { v in
                if model.drag == nil {
                    guard abs(v.translation.width) > abs(v.translation.height) * 1.3 else { return }
                    model.beginDrag(side: side, startingOpen: true)
                }
                model.updateDrag(translationX: v.translation.width)
            }
            .onEnded { v in
                if model.drag != nil { model.endDrag(velocityX: v.velocity.width) }
            }
    }
}

// MARK: - Left panel

struct AddressPanel: View {
    @Bindable var model: BrowserModel
    @State private var draft = ""
    @FocusState private var focused: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer(minLength: model.regularWidth ? 40 : 24)
                heading("Address Bar")
                addressField
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                securityLine
                    .padding(.horizontal, 20)
                    .padding(.top, 6)

                HStack(alignment: .firstTextBaseline) {
                    heading("Bookmarks")
                    Spacer()
                    Button("Manage") { model.close(); model.sheet = .settings }
                        .font(.subheadline)
                        .foregroundStyle(SophiaColor.panelMuted)
                        .padding(.trailing, 16)
                        .accessibilityIdentifier("panel_manage_bookmarks")
                }
                .padding(.top, 28)
                PanelSeparator().padding(.top, 8)
                ForEach(PortalBookmark.allCases) { b in
                    let url = model.settings.url(for: b)
                    PanelRow(symbol: "globe", title: b.title, subtitle: url == nil ? "Not set up yet" : nil) {
                        model.openBookmark(b)
                    }
                    .accessibilityIdentifier("panel_bookmark_\(b.rawValue)")
                }

                if model.regularWidth {
                    RecentPages(model: model)
                        .padding(.top, 24)
                }

                heading("Sophia").padding(.top, 28)
                PanelSeparator().padding(.top, 8)
                PanelRow(symbol: "sparkles", title: "Study Assistant", subtitle: nil) { model.openAssistant(with: "") }
                    .accessibilityIdentifier("panel_assistant")
                PanelRow(symbol: "arrow.down.circle", title: "Downloads",
                         subtitle: model.downloads.activeCount > 0 ? "\(model.downloads.activeCount) in progress" : nil) {
                    model.close(); model.sheet = .downloads
                }
                .accessibilityIdentifier("panel_downloads")
                PanelRow(symbol: "clock", title: "History", subtitle: nil) { model.close(); model.sheet = .history }
                    .accessibilityIdentifier("panel_history")
                Spacer(minLength: 24)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .foregroundStyle(SophiaColor.panelContent)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Address and bookmarks")
        .accessibilityIdentifier("address_panel")
        .onAppear {
            draft = UrlPolicy.displayURL(model.url)
            if model.focusAddress {
                focused = true
                model.focusAddress = false
            }
        }
        .onChange(of: model.url) { _, new in if !focused { draft = UrlPolicy.displayURL(new) } }
        .onChange(of: model.focusAddress) { _, want in
            if want { focused = true; model.focusAddress = false }
        }
    }

    private func heading(_ text: String) -> some View {
        Text(text)
            .font(.title2.weight(.bold))
            .padding(.horizontal, 16)
            .accessibilityAddTraits(.isHeader)
    }

    private var addressField: some View {
        HStack(spacing: 6) {
            TextField("Search or type a web address", text: $draft)
                .focused($focused)
                .keyboardType(.webSearch)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .onSubmit(go)
                .font(.body)
                .accessibilityLabel("Address")
                .accessibilityIdentifier("panel_address")
            if focused && !draft.isEmpty {
                Button { draft = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(SophiaColor.panelMuted) }
                    .accessibilityLabel("Clear address")
            }
            Button(action: go) {
                Image(systemName: "arrow.right")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color(white: 0.43))
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(.white))
            }
            .accessibilityLabel("Go")
            .accessibilityIdentifier("panel_go")
            .hoverEffect(.lift)
        }
        .padding(.leading, 14)
        .padding(.trailing, 6)
        .frame(minHeight: 52)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(SophiaColor.panelField))
    }

    @ViewBuilder private var securityLine: some View {
        switch UrlPolicy.securityLevel(for: model.url) {
        case .secure:
            Label("Secure connection", systemImage: "lock.fill").font(.caption).foregroundStyle(SophiaColor.panelMuted)
        case .local:
            Label("This device", systemImage: "iphone").font(.caption).foregroundStyle(SophiaColor.panelMuted)
        case .insecure:
            Label("Not secure", systemImage: "lock.slash").font(.caption).foregroundStyle(.yellow)
        case .none:
            EmptyView()
        }
    }

    private func go() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        focused = false
        model.submitAddress(text)
    }
}

/// iPad only: the last few pages, so the larger panel is useful rather than empty.
private struct RecentPages: View {
    let model: BrowserModel
    @Query(RecentPages.descriptor) private var recent: [HistoryEntry]

    static var descriptor: FetchDescriptor<HistoryEntry> {
        var d = FetchDescriptor<HistoryEntry>(sortBy: [SortDescriptor(\.visitedAt, order: .reverse)])
        d.fetchLimit = 5
        return d
    }

    var body: some View {
        if !recent.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Text("Recent")
                    .font(.title2.weight(.bold))
                    .padding(.horizontal, 16)
                    .accessibilityAddTraits(.isHeader)
                PanelSeparator().padding(.top, 8)
                ForEach(recent) { e in
                    PanelRow(symbol: "clock.arrow.circlepath", title: e.title.isEmpty ? UrlPolicy.host(of: e.url) : e.title,
                             subtitle: UrlPolicy.host(of: e.url)) {
                        model.open(e.url)
                        model.close()
                    }
                }
            }
        }
    }
}

struct PanelSeparator: View {
    var inset: CGFloat = 0
    var body: some View {
        Rectangle().fill(SophiaColor.panelSeparator).frame(height: 0.75).padding(.leading, inset)
    }
}

struct PanelRow: View {
    let symbol: String
    let title: String
    let subtitle: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 0) {
                HStack(spacing: 14) {
                    Image(systemName: symbol)
                        .font(.title3)
                        .frame(width: 28)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.body).lineLimit(1)
                        if let subtitle {
                            Text(subtitle).font(.caption).foregroundStyle(SophiaColor.panelMuted).lineLimit(1)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .padding(.leading, 16)
                .padding(.trailing, 8)
                .frame(minHeight: 56)
                PanelSeparator(inset: 58)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
    }
}

// MARK: - Right panel

struct ControlsPanel: View {
    @Bindable var model: BrowserModel

    var body: some View {
        let tile: CGFloat = model.regularWidth ? 92 : 76
        GeometryReader { geo in
        ScrollView {
            VStack(spacing: 14) {
                ControlTile(symbol: "chevron.left", label: "Back", size: tile, enabled: model.canGoBack) { model.goBack() }
                    .accessibilityIdentifier("control_back")
                ControlTile(symbol: "chevron.right", label: "Forward", size: tile, enabled: model.canGoForward) { model.goForward() }
                    .accessibilityIdentifier("control_forward")
                ControlTile(symbol: "arrow.clockwise", label: "Reload", size: tile, enabled: true) { model.reload(); model.close() }
                    .accessibilityIdentifier("control_reload")
                ControlTile(symbol: "gearshape", label: "Settings", size: tile, enabled: true) { model.close(); model.sheet = .settings }
                    .accessibilityIdentifier("control_settings")
            }
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, minHeight: geo.size.height)
        }
        .scrollBounceBehavior(.basedOnSize)
        }
        .scrollBounceBehavior(.basedOnSize)
        .foregroundStyle(SophiaColor.panelContent)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Browser controls")
        .accessibilityIdentifier("controls_panel")
    }
}

struct ControlTile: View {
    let symbol: String
    let label: String
    let size: CGFloat
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: symbol).font(.system(size: size * 0.32, weight: .medium))
                Text(label).font(.caption.weight(.medium)).lineLimit(1).minimumScaleFactor(0.8)
            }
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(SophiaColor.panelTile))
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.38)
        .hoverEffect(.lift)
        .accessibilityLabel(label)
    }
}

/// Optional small tabs at the screen edges for people who prefer tapping to swiping
/// (Settings > Accessibility). Off by default, so the normal browser state stays untouched.
struct PanelHandles: View {
    let model: BrowserModel

    var body: some View {
        HStack {
            handle(edge: .leading, label: "Show address and bookmarks", id: "handle_address") { model.show(.address, focusAddress: true) }
            Spacer()
            handle(edge: .trailing, label: "Show browser controls", id: "handle_controls") { model.show(.controls) }
        }
        .ignoresSafeArea()
    }

    private func handle(edge: HorizontalEdge, label: String, id: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Capsule()
                .fill(Color(white: 0.5).opacity(0.55))
                .frame(width: 5, height: 48)
                .frame(width: 22, height: 96)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier(id)
    }
}
