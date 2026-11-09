import SwiftUI

/// Sophia's palette: maroon, white and neutral grey, the same as Sophia for Android.
enum SophiaColor {
    static let maroon = Color("Maroon")
    static let maroonUI = UIColor(red: 0x7A / 255, green: 0x1B / 255, blue: 0x1B / 255, alpha: 1)

    /// Side panels: translucent neutral grey with white content, as in the reference design.
    /// This colour is only ever drawn inside an open panel, never over the page.
    static let panel = Color(white: 0.50).opacity(0.95)
    static let panelField = Color.white.opacity(0.16)
    static let panelTile = Color.white.opacity(0.14)
    static let panelSeparator = Color.white.opacity(0.28)
    static let panelContent = Color.white
    static let panelMuted = Color.white.opacity(0.62)
}

/// The Sophia mark: a white stylised S on a solid maroon circle. Drawn as a vector so it stays sharp
/// at every size; it uses the same path as the Android icon.
struct SophiaMark: View {
    var size: CGFloat = 64

    var body: some View {
        ZStack {
            Circle().fill(SophiaColor.maroon)
            SophiaS()
                .stroke(.white, style: StrokeStyle(lineWidth: size * 100 / 1024, lineCap: .round))
        }
        .frame(width: size, height: size)
        .accessibilityLabel("Sophia")
    }
}

/// The S stroke in a 1024 grid, scaled to the rect.
struct SophiaS: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 1024
        let ox = rect.minX + (rect.width - 1024 * s) / 2
        let oy = rect.minY + (rect.height - 1024 * s) / 2
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: ox + x * s, y: oy + y * s) }
        var p = Path()
        p.move(to: pt(615.9, 332))
        p.addArc(center: pt(512, 392), radius: 120 * s, startAngle: .degrees(-30), endAngle: .degrees(90), clockwise: true)
        p.addArc(center: pt(512, 632), radius: 120 * s, startAngle: .degrees(-90), endAngle: .degrees(150), clockwise: false)
        return p
    }
}

enum AppearanceMode: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "System default"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}
