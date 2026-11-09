import Foundation

/// Which side panel is showing. `.none` is the normal browser state: nothing is drawn over the page.
public enum PanelState: String, Sendable {
    case none
    /// Left edge: address bar and bookmarks.
    case address
    /// Right edge: Back, Forward, Reload, Settings.
    case controls
}

public enum PanelSide: Sendable { case left, right }

/// The numbers behind Sophia's edge swipes, kept free of UIKit so they are unit-tested.
///
/// A swipe only counts when it starts inside a narrow strip at the screen edge and moves clearly
/// horizontally away from that edge. Touches anywhere else go straight to the web page, so scrolling,
/// text selection and forms behave as in Safari.
public enum EdgeGesture {
    /// Width of the edge strip that can start a swipe, in points.
    public static func edgeZone(width: Double, regular: Bool) -> Double {
        let base = regular ? 32.0 : 24.0
        return min(base, width * 0.08)
    }

    /// Horizontal movement must be at least this many times the vertical movement.
    public static let minHorizontalRatio = 1.8

    /// Decides, when a pan begins, whether it is the start of an edge swipe and from which side.
    /// `start` is where the finger first touched; `translation` is how far it has moved so far.
    public static func beginning(startX: Double, width: Double, translationX: Double, translationY: Double, regular: Bool) -> PanelSide? {
        guard width > 0 else { return nil }
        guard abs(translationX) >= minHorizontalRatio * abs(translationY), abs(translationX) > 0 else { return nil }
        let zone = edgeZone(width: width, regular: regular)
        if startX <= zone && translationX > 0 { return .left }
        if startX >= width - zone && translationX < 0 { return .right }
        return nil
    }

    /// How far the panel is revealed (0...1) for a drag of `translationX` points.
    /// For the left panel, positive translation reveals; for the right panel, negative.
    public static func progress(side: PanelSide, translationX: Double, panelWidth: Double, startingOpen: Bool) -> Double {
        guard panelWidth > 0 else { return 0 }
        let signed = side == .left ? translationX : -translationX
        let base = startingOpen ? 1.0 : 0.0
        return min(1, max(0, base + signed / panelWidth))
    }

    /// Velocity in points per second that commits the gesture regardless of distance.
    public static let flickVelocity = 550.0

    /// Whether a finished drag leaves the panel open.
    public static func settlesOpen(side: PanelSide, progress: Double, velocityX: Double) -> Bool {
        let towardOpen = side == .left ? velocityX : -velocityX
        if towardOpen > flickVelocity { return true }
        if towardOpen < -flickVelocity { return false }
        return progress >= 0.4
    }

    /// Panel width in points for the current window.
    public static func panelWidth(_ state: PanelState, windowWidth: Double, regular: Bool) -> Double {
        switch state {
        case .none: return 0
        case .address: return min(regular ? 380 : 300, windowWidth * (regular ? 0.5 : 0.82))
        case .controls: return regular ? 128 : 104
        }
    }
}
