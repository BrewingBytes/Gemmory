import XCTest

/// The Stage 0 frame gate (eng D12, T59): the app runs its 10 s pan-and-zoom over the largest bundled canvas
/// (`-bench`) and reports frame intervals and draw times; the gate is p95 ≤ 8.3 ms at 120 Hz on the iPhone 17 Pro.
/// The signpost metric records each canvas draw (the app's `SignpostProbe`) for Xcode's test report.
/// On the simulator the run only proves the harness: its numbers aren't the gate.
final class FrameGateTests: XCTestCase {
    static let gateMs = 8.3

    func testPanAndZoomLargestCanvas() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-bench"]
        let metric = XCTOSSignpostMetric(subsystem: "com.brewingbytes.gemmory", category: "frame", name: "draw")
        let options = XCTMeasureOptions()
        options.iterationCount = 1
        var report = ""
        measure(metrics: [metric], options: options) {
            app.launch()
            report = Self.waitForResult(app)
            app.terminate()
        }
        let values = Self.parse(report)
        let attachment = XCTAttachment(string: report)
        attachment.name = "bench"
        attachment.lifetime = .keepAlways
        add(attachment)
        print("GemmoryBench \(report)")
        let p95 = try XCTUnwrap(values["interval_p95"], "no interval_p95 in \(report)")
        let draw = try XCTUnwrap(values["draw_p95"], "no draw_p95 in \(report)")
        #if targetEnvironment(simulator)
        throw XCTSkip("simulator: harness only (interval_p95=\(p95) ms, draw_p95=\(draw) ms)")
        #else
        XCTAssertLessThanOrEqual(draw, Self.gateMs, "draw p95 over the gate: \(report)")
        // A 120 Hz vsync is 8.33 ms, so the interval's gate allows that one period, not 8.3 ms.
        XCTAssertLessThanOrEqual(p95, 8.34, "frame interval p95 over one 120 Hz period: \(report)")
        #endif
    }

    private static func waitForResult(_ app: XCUIApplication) -> String {
        let result = app.descendants(matching: .any)["bench-result"]
        XCTAssertTrue(result.waitForExistence(timeout: 30))
        let done = NSPredicate(format: "label CONTAINS 'frames='")
        let expectation = XCTNSPredicateExpectation(predicate: done, object: result)
        _ = XCTWaiter.wait(for: [expectation], timeout: 40)
        return result.label
    }

    static func parse(_ report: String) -> [String: Double] {
        var values: [String: Double] = [:]
        for part in report.split(separator: " ") {
            let kv = part.split(separator: "=")
            if kv.count == 2, let v = Double(kv[1]) { values[String(kv[0])] = v }
        }
        return values
    }
}
