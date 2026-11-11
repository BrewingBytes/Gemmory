import XCTest

/// Screenshots for PR bodies (T60, AGENTS.md: every PR shows what it changes). Each screen is attached as
/// `screen-<n>-<name>`; `ios.yml` exports the attachments from each simulator's result bundle and uploads them as the
/// `ios-screenshots` artifact (docs/agents/workflow.md → iOS). A PR that adds or changes a screen adds a shot here.
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func testScreens() {
        let app = XCUIApplication()
        app.launchArguments = ["-reset", "-AppleLanguages", "(en)"]
        app.launch()

        let canvas = app.descendants(matching: .any)["canvas"]
        XCTAssertTrue(canvas.waitForExistence(timeout: 20))
        shoot("1-canvas-fit")

        // At fit, a tap zooms in to the placing zoom (design system zoom table). Shoot once the status line shows the
        // placing zoom and it has stopped changing, so a slow runner can't catch the animation halfway.
        let fit = zoom(app)
        canvas.tap()
        XCTAssertTrue(waitForSettledZoom(app, above: fit), "the canvas didn't zoom in")
        shoot("2-canvas-zoomed")

        let checks = app.descendants(matching: .any)["checks"]
        XCTAssertTrue(checks.waitForExistence(timeout: 10))
        checks.tap()
        XCTAssertTrue(app.descendants(matching: .any)["percent"].waitForExistence(timeout: 10))
        shoot("3-checks")
    }

    private func zoom(_ app: XCUIApplication) -> Double {
        FrameGateTests.parse(app.descendants(matching: .any)["status"].label)["zoom"] ?? 0
    }

    /// True once the zoom is at least 16 (the placing zoom), above [fit], and the same on two reads 0.3 s apart.
    private func waitForSettledZoom(_ app: XCUIApplication, above fit: Double) -> Bool {
        let deadline = Date().addingTimeInterval(10)
        var last = -1.0
        while Date() < deadline {
            let now = zoom(app)
            if now >= 16 && now > fit && now == last { return true }
            last = now
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        return false
    }

    private func shoot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "screen-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
