import XCTest

/// The Stage 0 checks (T59, eng D23, design D2) on iOS. Each language is set the way iOS's per-app language
/// setting sets it (`-AppleLanguages`); Compose Resources reads it from `NSLocale.preferredLanguages`.
final class StageZeroChecksTests: XCTestCase {
    /// Plural categories by quantity (0, 1, 2, 5, 20, 22, 101), from CLDR.
    func testPluralsFollowTheAppLanguage() {
        let expected: [String: [Int: String]] = [
            "en": [0: "other", 1: "one", 2: "other", 5: "other", 20: "other", 22: "other", 101: "other"],
            "pl": [0: "many", 1: "one", 2: "few", 5: "many", 20: "many", 22: "few", 101: "many"],
            "ro": [0: "few", 1: "one", 2: "few", 5: "few", 20: "other", 22: "other", 101: "few"],
            "ja": [0: "other", 1: "other", 2: "other", 5: "other", 20: "other", 22: "other", 101: "other"],
        ]
        for (language, categories) in expected.sorted(by: { $0.key < $1.key }) {
            let app = openChecks(language: language)
            var seen: [String] = []
            for (n, category) in categories.sorted(by: { $0.key < $1.key }) {
                let label = text("plural-\(n)", in: app)
                seen.append("\(n)=\(label)")
                XCTAssertEqual(label, "\(category):\(n)", "\(language) \(n)")
            }
            print("StageZero plurals \(language): \(seen.joined(separator: " "))")
            print("StageZero percent \(language): \(text("percent", in: app))")
            app.terminate()
        }
    }

    /// Android's `%%` is a literal percent sign. Compose Resources 1.12.1 shows "40%%" (T59 finding), which sends
    /// Stage 1b to our own string reader (eng D23). When a release fixes it, this test fails: re-check D23 then.
    func testPercentSign() {
        let app = openChecks(language: "en")
        let shown = text("percent", in: app)
        print("StageZero percent: \(shown)")
        XCTExpectFailure("Compose Resources 1.12.1 leaves %% as is (eng D23)")
        XCTAssertEqual(shown, "40% done")
    }

    /// Dynamic Type follows live: the shell script changes the text size while this waits (README → iOS spike).
    func testDynamicTypeFollowsLive() throws {
        guard ProcessInfo.processInfo.environment["GEMMORY_DYNAMIC_TYPE"] != nil else {
            throw XCTSkip("needs the text size changed from outside (GEMMORY_DYNAMIC_TYPE)")
        }
        let app = openChecks(language: "en")
        let first = text("font-scale", in: app)
        print("StageZero fontScale before: \(first)")
        let deadline = Date().addingTimeInterval(120)
        var now = first
        while Date() < deadline && now == first {
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
            now = text("font-scale", in: app)
        }
        print("StageZero fontScale after: \(now)")
        XCTAssertNotEqual(now, first, "the font scale didn't follow the text size live")
    }

    /// Prints the font scale Compose sees at launch, for a run with the text size set beforehand.
    func testFontScaleAtLaunch() {
        let app = openChecks(language: "en")
        print("StageZero fontScale at launch: \(text("font-scale", in: app))")
    }

    /// Design D2: the iOS back swipe works only while the screen's back handler is enabled.
    func testBackSwipeFollowsTheHandler() {
        let app = openChecks(language: "en")
        let toggle = app.descendants(matching: .any)["back-toggle"]
        toggle.tap()
        XCTAssertTrue(app.descendants(matching: .any)["back-toggle"].waitForExistence(timeout: 5))
        edgeSwipe(app)
        XCTAssertTrue(app.descendants(matching: .any)["percent"].waitForExistence(timeout: 3), "back swipe left Checks while off")
        app.descendants(matching: .any)["back-toggle"].tap()
        edgeSwipe(app)
        XCTAssertTrue(app.descendants(matching: .any)["canvas"].waitForExistence(timeout: 5), "back swipe didn't go back while on")
    }

    private func openChecks(language: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(\(language))"]
        app.launch()
        let checks = app.descendants(matching: .any)["checks"]
        XCTAssertTrue(checks.waitForExistence(timeout: 20))
        checks.tap()
        XCTAssertTrue(app.descendants(matching: .any)["percent"].waitForExistence(timeout: 10))
        return app
    }

    private func text(_ tag: String, in app: XCUIApplication) -> String {
        let element = app.descendants(matching: .any)[tag]
        _ = element.waitForExistence(timeout: 5)
        return element.label.isEmpty ? (element.value as? String ?? "") : element.label
    }

    private func edgeSwipe(_ app: XCUIApplication) {
        let window = app.windows.firstMatch
        let start = window.coordinate(withNormalizedOffset: CGVector(dx: 0.005, dy: 0.5))
        let end = window.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: 0)
    }
}
