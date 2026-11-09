import Foundation

/// Product facts shared with Sophia for Android (android/core/.../Constants.kt).
public enum SophiaConstants {
    public static let appName = "Sophia"
    public static let tagline = "Browse. Learn. Understand."
    public static let description = "An Educational Browser & AI Study Companion."
    public static let nameMeaning = "From the Greek sophía, meaning wisdom."
    public static let publisher = "KRLBRYN"
    public static let defaultPortalURL = "https://discipulusv2.amasystem.net/"
    public static let repoURL = "https://github.com/kerlbryandomingo-wq/lapsus"
    public static let privacyPolicyPath = "ios/docs/PRIVACY.md"
    public static var privacyPolicyURL: String { repoURL + "/blob/main/" + privacyPolicyPath }

    /// Registrable domains treated as part of the student portal.
    public static let portalDomains = ["amasystem.net", "amaes.edu.ph"]

    public static let maxHistoryEntries = 5000
    public static let maxAIInputChars = 12_000
}

/// The fixed bookmarks in the left panel. Discipulus is built in; the LMS and Aurora Login
/// addresses are not known anywhere in the project yet, so they stay empty until the student
/// (or a later release) sets them. Nothing is guessed.
public enum PortalBookmark: String, CaseIterable, Identifiable, Sendable {
    case discipulus
    case lms
    case aurora

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .discipulus: return "Discipulus"
        case .lms: return "LMS"
        case .aurora: return "Aurora Login"
        }
    }

    /// The built-in address, or nil when it must be configured in Settings.
    public var builtInURL: String? {
        switch self {
        case .discipulus: return SophiaConstants.defaultPortalURL
        case .lms, .aurora: return nil
        }
    }
}
