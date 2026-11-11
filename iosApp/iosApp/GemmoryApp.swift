import SwiftUI
import UIKit
import os
import GemmoryKit

/// The iOS shell (T59 Stage 0): shows the shared Compose UI and brackets each canvas draw in a signpost interval,
/// which the frame-gate XCTest reads (eng D12).
@main
struct GemmoryApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let args = ProcessInfo.processInfo.arguments
        return MainViewControllerKt.MainViewController(
            probe: SignpostProbe(), autoBench: args.contains("-bench"), reset: args.contains("-reset"))
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// One "draw" interval per canvas draw, in the subsystem and category `FrameGateTests` measures.
final class SignpostProbe: FrameProbe {
    static let subsystem = "com.brewingbytes.gemmory"
    static let category = "frame"
    private let signposter = OSSignposter(subsystem: subsystem, category: category)
    private var state: OSSignpostIntervalState?

    func beginDraw() {
        state = signposter.beginInterval("draw")
    }

    func endDraw() {
        if let state { signposter.endInterval("draw", state) }
        state = nil
    }
}

