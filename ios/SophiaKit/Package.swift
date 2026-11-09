// swift-tools-version:5.9
// SophiaKit holds Sophia's platform-independent logic: URL rules, edge-swipe decisions, the Study
// Assistant's prompts, parsing and providers. It uses Foundation only, so `swift test` also runs on
// Linux, and the iOS app links it as a local package.
import PackageDescription

let package = Package(
    name: "SophiaKit",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SophiaKit", targets: ["SophiaKit"]),
    ],
    targets: [
        .target(name: "SophiaKit"),
        .testTarget(name: "SophiaKitTests", dependencies: ["SophiaKit"]),
    ]
)
