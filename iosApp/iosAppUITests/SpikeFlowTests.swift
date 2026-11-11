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
    private func pick(slot: Int) {
        let circle = app.descendants(matching: .any)["slot-\(slot - 1)"]
        let tray = app.descendants(matching: .any)["tray"]
        var swipes = 0
        while !(circle.exists && circle.isHittable) && swipes < 12 {
            tray.swipeLeft(velocity: .slow)
            swipes += 1
        }
        // A tap during the fling only stops the scroll, so wait until the circle stops moving.
        var last = circle.frame
        for _ in 0..<30 {
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
            let now = circle.frame
            if now == last { break }
            last = now
        }
        // The accessibility frame can lag a Compose fling, so a tap may only stop the scroll (as a touch does on
        // Android too). Tap again until the status line shows the slot; the selection itself isn't being tested here.
        for attempt in 1...3 {
            circle.tap()
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
