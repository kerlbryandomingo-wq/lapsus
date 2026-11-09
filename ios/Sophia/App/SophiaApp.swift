import SwiftUI
import SwiftData
import SophiaKit

/// How this launch was started. UI tests pass `-ui-testing` to get a clean, offline-safe browser.
struct LaunchOptions {
    let uiTesting: Bool
    let useFixturePage: Bool

    static let current: LaunchOptions = {
        let args = ProcessInfo.processInfo.arguments
        let ui = args.contains("-ui-testing")
        return LaunchOptions(uiTesting: ui, useFixturePage: ui && !args.contains("-live-portal"))
    }()
}

/// Services shared by every Sophia window.
@MainActor
final class AppServices {
    let settings: SettingsStore
    let downloads: DownloadManager
    let network = NetworkMonitor()
    let container: ModelContainer

    init(options: LaunchOptions) {
        if options.uiTesting {
            let defaults = UserDefaults(suiteName: "com.krlbryn.sophia.uitests")!
            defaults.removePersistentDomain(forName: "com.krlbryn.sophia.uitests")
            // Show the status bar in screenshots of the reference states only when asked.
            settings = SettingsStore(defaults: defaults)
            KeychainStore(service: "com.krlbryn.sophia.uitests").deleteAll()
        } else {
            settings = SettingsStore()
        }
        downloads = DownloadManager()
        container = SophiaStore.makeContainer(inMemory: options.uiTesting)
    }
}

@main
struct SophiaApp: App {
    @State private var services = AppServices(options: .current)

    var body: some Scene {
        WindowGroup {
            BrowserWindow(services: services)
                .environment(services.network)
                .environment(services.settings)
                .preferredColorScheme(services.settings.appearance.colorScheme)
                .tint(SophiaColor.maroon)
        }
        .modelContainer(services.container)
        .commands { BrowserCommands() }
    }
}

/// One browser window. On iPad each window (Split View, Stage Manager) gets its own page.
struct BrowserWindow: View {
    let services: AppServices
    @State private var model: BrowserModel?

    var body: some View {
        Group {
            if let model {
                BrowserView(model: model)
            } else {
                StartupView()
            }
        }
        .onAppear {
            if model == nil {
                model = BrowserModel(
                    settings: services.settings,
                    downloads: services.downloads,
                    fixtureHTML: LaunchOptions.current.useFixturePage ? FixturePage.html : nil
                )
            }
        }
    }
}
