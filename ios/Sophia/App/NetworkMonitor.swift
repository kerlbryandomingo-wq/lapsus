import Foundation
import Network
import Observation

/// Tells the browser when the device goes offline or comes back, so an error page can retry by itself.
@MainActor
@Observable
final class NetworkMonitor {
    private(set) var isConnected = true
    @ObservationIgnored private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let connected = path.status == .satisfied
            Task { @MainActor in self?.isConnected = connected }
        }
        monitor.start(queue: DispatchQueue(label: "com.krlbryn.sophia.network"))
    }

    deinit { monitor.cancel() }
}
