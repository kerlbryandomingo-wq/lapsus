import XCTest
@testable import SophiaKit

final class FormattingTests: XCTestCase {
    func testBytes() {
        XCTAssertEqual(Formatting.bytes(-1), "Unknown size")
        XCTAssertEqual(Formatting.bytes(512), "512 B")
        XCTAssertEqual(Formatting.bytes(1536), "1.5 KB")
        XCTAssertEqual(Formatting.bytes(25 * 1024 * 1024), "25 MB")
        XCTAssertEqual(Formatting.progress(received: 1024, total: 0), "1.0 KB")
    }

    func testSanitize() {
        XCTAssertEqual(FileNames.sanitize("../../etc/passwd"), "passwd")
        XCTAssertEqual(FileNames.sanitize("a:b*c?.pdf"), "a_b_c_.pdf")
        XCTAssertEqual(FileNames.sanitize("   "), "download")
        XCTAssertEqual(FileNames.sanitize(nil, fallback: "file"), "file")
        let long = String(repeating: "x", count: 300) + ".docx"
        let s = FileNames.sanitize(long)
        XCTAssertEqual(s.count, 120)
        XCTAssertTrue(s.hasSuffix(".docx"))
    }

    func testUniqueAndTypes() {
        XCTAssertEqual(FileNames.unique("a.pdf", existing: ["a.pdf", "a (1).pdf"]), "a (2).pdf")
        XCTAssertEqual(FileNames.unique("readme", existing: ["readme"]), "readme (1)")
        XCTAssertEqual(FileNames.typeLabel("x.pdf", mimeType: nil), "PDF document")
        XCTAssertEqual(FileNames.typeLabel("x", mimeType: "image/png"), "Image")
        XCTAssertEqual(FileNames.typeLabel("x.xyz", mimeType: nil), "XYZ file")
        XCTAssertEqual(FileNames.typeLabel("x", mimeType: nil), "File")
    }
}
