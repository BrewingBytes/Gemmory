import XCTest

/// The spike's play loop on iOS (T59 done-when): tap to zoom, pinch zoom and pan, tap to place with a wrong color
/// rejected and nothing changed, and save and resume across a relaunch. Reads the app's status line.
final class SpikeFlowTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-reset"]
        app.launch()
    }

    func testZoomPanPlaceRejectAndResume() throws {
        let canvas = app.descendants(matching: .any)["canvas"]
        XCTAssertTrue(canvas.waitForExistence(timeout: 20))
        let fit = status()
        XCTAssertEqual(fit["placed"], 0)

        // At fit, a tap zooms in to the placing zoom (design system zoom table).
        canvas.tap()
        let zoomed = waitFor { $0["zoom"]! > fit["zoom"]! }
        XCTAssertGreaterThanOrEqual(zoomed["zoom"]!, 16)

        // Pinch out: bigger cells. Swipe: the center moves.
        canvas.pinch(withScale: 1.6, velocity: 2)
        let pinched = waitFor { $0["zoom"]! > zoomed["zoom"]! }
        canvas.swipeLeft()
        let panned = waitFor { $0["cx"]! != pinched["cx"]! }
        XCTAssertEqual(panned["placed"], 0)

        // The middle cell (canvas.tap() taps it) with a wrong color: rejected, nothing changes.
        let mid = Int(panned["mid"]!)
        XCTAssertGreaterThan(mid, 0)
        let wrong = mid == 1 ? 2 : 1
        pick(slot: wrong)
        let before = waitFor { $0["slot"]! == Double(wrong) }
        canvas.tap()
        let rejected = waitFor { $0["rejected"]! > before["rejected"]! }
        XCTAssertEqual(rejected["placed"], 0, "a wrong color changed the canvas")
        XCTAssertEqual(rejected["zoom"], before["zoom"])
        XCTAssertEqual(rejected["cx"], before["cx"])

        // The right color places it.
        pick(slot: mid)
        _ = waitFor { $0["slot"]! == Double(mid) }
        canvas.tap()
        _ = waitFor { $0["placed"]! == 1 }

        // The same cell again is rejected (already placed), and nothing changes.
        let once = status()
        canvas.tap()
        let again = waitFor { $0["rejected"]! > once["rejected"]! }
        XCTAssertEqual(again["placed"], 1)

        // Save and resume: the placement survives a relaunch.
        app.terminate()
        app.launchArguments = []
        app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["canvas"].waitForExistence(timeout: 20))
        let resumed = waitFor { $0["placed"] != nil }
        XCTAssertEqual(resumed["placed"], 1)
    }

    /// Selects color [slot] (1-based), scrolling the tray until its circle is on screen.
    ///
    /// On Compose iOS a `LazyRow`'s accessibility frames stop changing before its fling does (lessons, 2026-11-11), so
    /// a circle seen on screen can still slide past the tray's edge before the tap. Each attempt therefore brings the
    /// circle back on screen first (swiping whichever way it went past) and taps the point where it is now: a
    /// coordinate tap, which misses harmlessly where `circle.tap()` throws "Activation point invalid" on a circle that
    /// has left the screen (#187's iOS run). The status line says whether the slot was taken.
    private func pick(slot: Int) {
        let circle = app.descendants(matching: .any)["slot-\(slot - 1)"]
        let tray = app.descendants(matching: .any)["tray"]
        for attempt in 1...4 {
            guard bringIntoView(circle, in: tray) else {
                XCTFail("slot \(slot) never stayed in the tray: circle \(String(describing: frame(of: circle))), tray \(tray.frame)")
                return
            }
            let frame = circle.frame
            app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
            let deadline = Date().addingTimeInterval(2)
            while Date() < deadline {
                if status()["slot"] == Double(slot) {
                    if attempt > 1 { print("pick(slot: \(slot)) needed \(attempt) taps") }
                    return
                }
                RunLoop.current.run(until: Date().addingTimeInterval(0.1))
            }
        }
        XCTFail("couldn't select slot \(slot); status: \(app.descendants(matching: .any)["status"].label)")
    }

    /// Scrolls the tray until [circle] is on screen and has stopped there: swipes left while it's past the right edge
    /// and right once a fling carried it past the left one. False if it never settles on screen.
    private func bringIntoView(_ circle: XCUIElement, in tray: XCUIElement) -> Bool {
        for _ in 0..<16 {
            settle(circle)
            if onScreen(circle, in: tray) { return true }
            if let frame = frame(of: circle), !frame.isEmpty, frame.minX < tray.frame.minX {
                tray.swipeRight(velocity: .slow)
            } else {
                tray.swipeLeft(velocity: .slow)
            }
        }
        settle(circle)
        return onScreen(circle, in: tray)
    }

    /// Waits until [element]'s frame holds still for a while. The frame can freeze before the fling ends, so a still
    /// frame is read again after a pause before it counts. A circle the tray hasn't laid out yet has no element, and
    /// reading its frame throws, so a missing one reads as no frame.
    private func settle(_ element: XCUIElement) {
        var last = frame(of: element)
        var still = 0
        for _ in 0..<40 where still < 3 {
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
            let now = frame(of: element)
            still = now == last ? still + 1 : 0
            last = now
        }
    }

    /// [element]'s frame, or nil while it doesn't exist (reading a missing element's frame fails the test).
    private func frame(of element: XCUIElement) -> CGRect? {
        element.exists ? element.frame : nil
    }

    /// Whether [element] lies across the tray's width (1 pt slack for rounding). Not `isHittable`: on the iPad's wider
    /// tray Compose lays out circles past the edge, and asking one of those for hittability fails the test
    /// ("Activation point invalid"). Only the horizontal extent counts, since the tray scrolls sideways.
    private func onScreen(_ element: XCUIElement, in tray: XCUIElement) -> Bool {
        guard element.exists else { return false }
        let frame = element.frame, bounds = tray.frame
        return !frame.isEmpty && frame.minX >= bounds.minX - 1 && frame.maxX <= bounds.maxX + 1
    }

    private func status() -> [String: Double] {
        let label = app.descendants(matching: .any)["status"].label
        return FrameGateTests.parse(label)
    }

    private func waitFor(_ condition: ([String: Double]) -> Bool) -> [String: Double] {
        let deadline = Date().addingTimeInterval(10)
        while Date() < deadline {
            let s = status()
            if !s.isEmpty && condition(s) { return s }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        XCTFail("timed out; status: \(app.descendants(matching: .any)["status"].label)")
        return status()
    }
}
