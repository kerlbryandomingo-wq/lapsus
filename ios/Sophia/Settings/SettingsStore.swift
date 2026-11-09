import Foundation
import Observation
import SophiaKit

enum NewWindowMode: String, CaseIterable, Identifiable {
    case popup, sameWindow
    var id: String { rawValue }
    var label: String { self == .popup ? "Pop-up sheet" : "Same page" }
}

enum PermissionKind: String, Codable, CaseIterable {
    case camera, microphone, cameraAndMicrophone
    var label: String {
        switch self {
        case .camera: return "Camera"
        case .microphone: return "Microphone"
        case .cameraAndMicrophone: return "Camera and microphone"
        }
    }
}

struct SitePermission: Codable, Hashable, Identifiable {
    var origin: String
    var kind: PermissionKind
    var allowed: Bool
    var id: String { origin + "|" + kind.rawValue }
}

/// Sophia's preferences, stored in UserDefaults. API keys are never stored here; they live in the
/// Keychain (see `KeychainStore`).
@Observable
final class SettingsStore {
    @ObservationIgnored private let defaults: UserDefaults

    var appearance: AppearanceMode { didSet { set(appearance.rawValue, "appearance") } }
    var homepage: String { didSet { set(homepage, "homepage") } }
    var searchEngine: SearchEngine { didSet { set(searchEngine.rawValue, "searchEngine") } }
    var javaScriptEnabled: Bool { didSet { set(javaScriptEnabled, "javaScriptEnabled") } }
    var historyEnabled: Bool { didSet { set(historyEnabled, "historyEnabled") } }
    var hideStatusBar: Bool { didSet { set(hideStatusBar, "hideStatusBar") } }
    var showPanelHandles: Bool { didSet { set(showPanelHandles, "showPanelHandles") } }
    var newWindowMode: NewWindowMode { didSet { set(newWindowMode.rawValue, "newWindowMode") } }
    var askBeforeDownloading: Bool { didSet { set(askBeforeDownloading, "askBeforeDownloading") } }
    var openDownloadsWhenDone: Bool { didSet { set(openDownloadsWhenDone, "openDownloadsWhenDone") } }
    /// Addresses for the LMS and Aurora Login bookmarks. Empty until the student sets them up.
    var lmsURL: String { didSet { set(lmsURL, "lmsURL") } }
    var auroraURL: String { didSet { set(auroraURL, "auroraURL") } }
    var sitePermissions: [SitePermission] {
        didSet { defaults.set(try? JSONEncoder().encode(sitePermissions), forKey: "sitePermissions") }
    }

    var aiProvider: AIProviderID { didSet { set(aiProvider.rawValue, "aiProvider") } }
    var openAIModel: String { didSet { set(openAIModel, "openAIModel") } }
    var anthropicModel: String { didSet { set(anthropicModel, "anthropicModel") } }
    var compatibleModel: String { didSet { set(compatibleModel, "compatibleModel") } }
    var compatibleBaseURL: String { didSet { set(compatibleBaseURL, "compatibleBaseURL") } }
    var responseDetail: ResponseDetail { didSet { set(responseDetail.rawValue, "responseDetail") } }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        func str(_ k: String, _ d: String) -> String { defaults.string(forKey: k) ?? d }
        func bool(_ k: String, _ d: Bool) -> Bool { defaults.object(forKey: k) == nil ? d : defaults.bool(forKey: k) }
        appearance = AppearanceMode(rawValue: str("appearance", "")) ?? .system
        homepage = str("homepage", SophiaConstants.defaultPortalURL)
        searchEngine = SearchEngine(rawValue: str("searchEngine", "")) ?? .google
        javaScriptEnabled = bool("javaScriptEnabled", true)
        historyEnabled = bool("historyEnabled", true)
        hideStatusBar = bool("hideStatusBar", true)
        showPanelHandles = bool("showPanelHandles", false)
        newWindowMode = NewWindowMode(rawValue: str("newWindowMode", "")) ?? .popup
        askBeforeDownloading = bool("askBeforeDownloading", true)
        openDownloadsWhenDone = bool("openDownloadsWhenDone", false)
        lmsURL = str("lmsURL", "")
        auroraURL = str("auroraURL", "")
        sitePermissions = (defaults.data(forKey: "sitePermissions")).flatMap { try? JSONDecoder().decode([SitePermission].self, from: $0) } ?? []
        aiProvider = AIProviderID(rawValue: str("aiProvider", "")) ?? .off
        openAIModel = str("openAIModel", AIProviderID.openai.defaultModel)
        anthropicModel = str("anthropicModel", AIProviderID.anthropic.defaultModel)
        compatibleModel = str("compatibleModel", "")
        compatibleBaseURL = str("compatibleBaseURL", "")
        responseDetail = ResponseDetail(rawValue: str("responseDetail", "")) ?? .standard
    }

    private func set(_ value: Any, _ key: String) { defaults.set(value, forKey: key) }

    /// The address for a bookmark, or nil while it isn't set up.
    func url(for bookmark: PortalBookmark) -> String? {
        switch bookmark {
        case .discipulus: return bookmark.builtInURL
        case .lms: return lmsURL.isEmpty ? nil : lmsURL
        case .aurora: return auroraURL.isEmpty ? nil : auroraURL
        }
    }

    func model(for provider: AIProviderID) -> String {
        switch provider {
        case .openai: return openAIModel
        case .anthropic: return anthropicModel
        case .compatible: return compatibleModel
        case .demo: return "demo"
        case .off: return ""
        }
    }

    func permission(origin: String, kind: PermissionKind) -> Bool? {
        sitePermissions.first { $0.origin == origin && $0.kind == kind }?.allowed
    }

    func setPermission(origin: String, kind: PermissionKind, allowed: Bool) {
        sitePermissions.removeAll { $0.origin == origin && $0.kind == kind }
        sitePermissions.append(SitePermission(origin: origin, kind: kind, allowed: allowed))
    }

    /// Restores every preference to its default. API keys are cleared separately.
    func resetAll() {
        for key in defaults.dictionaryRepresentation().keys where !key.hasPrefix("Apple") && !key.hasPrefix("NS") {
            defaults.removeObject(forKey: key)
        }
        let fresh = SettingsStore(defaults: defaults)
        appearance = fresh.appearance; homepage = fresh.homepage; searchEngine = fresh.searchEngine
        javaScriptEnabled = fresh.javaScriptEnabled; historyEnabled = fresh.historyEnabled; hideStatusBar = fresh.hideStatusBar
        showPanelHandles = fresh.showPanelHandles; newWindowMode = fresh.newWindowMode
        askBeforeDownloading = fresh.askBeforeDownloading; openDownloadsWhenDone = fresh.openDownloadsWhenDone
        lmsURL = ""; auroraURL = ""; sitePermissions = []
        aiProvider = .off; openAIModel = fresh.openAIModel; anthropicModel = fresh.anthropicModel
        compatibleModel = ""; compatibleBaseURL = ""; responseDetail = .standard
    }
}
