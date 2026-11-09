import Foundation

public enum Formatting {
    public static func bytes(_ size: Int64) -> String {
        if size < 0 { return "Unknown size" }
        if size < 1024 { return "\(size) B" }
        let units = ["KB", "MB", "GB", "TB"]
        var value = Double(size) / 1024
        var i = 0
        while value >= 1024 && i < units.count - 1 {
            value /= 1024
            i += 1
        }
        return String(format: value < 10 ? "%.1f %@" : "%.0f %@", locale: Locale(identifier: "en_US_POSIX"), value, units[i])
    }

    /// "3 MB of 10 MB", or just the received amount when the total is unknown.
    public static func progress(received: Int64, total: Int64) -> String {
        total > 0 ? "\(bytes(received)) of \(bytes(total))" : bytes(received)
    }
}

public enum FileNames {
    private static let unsafe = try! NSRegularExpression(pattern: "[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]")

    /// Makes a server-supplied file name safe to store: no paths, no control characters, bounded length.
    public static func sanitize(_ raw: String?, fallback: String = "download") -> String {
        var name = raw ?? ""
        if let slash = name.lastIndex(where: { $0 == "/" || $0 == "\\" }) { name = String(name[name.index(after: slash)...]) }
        name = unsafe.stringByReplacingMatches(in: name, range: NSRange(name.startIndex..., in: name), withTemplate: "_")
        name = name.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "."))
        if name.isEmpty { name = fallback }
        if name.count > 120 {
            let ext = extensionOf(name)
            var stem = String(name.prefix(120 - ext.count - 1))
            while stem.hasSuffix(".") { stem.removeLast() }
            name = stem + (ext.isEmpty ? "" : "." + ext)
        }
        return name
    }

    public static func extensionOf(_ name: String) -> String {
        guard let dot = name.lastIndex(of: "."), dot != name.startIndex else { return "" }
        let ext = name[name.index(after: dot)...]
        guard (1...10).contains(ext.count), ext.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber) }) else { return "" }
        return ext.lowercased()
    }

    /// A name that does not collide with `existing`: "notes.pdf", "notes (1).pdf", ...
    public static func unique(_ name: String, existing: Set<String>) -> String {
        if !existing.contains(name) { return name }
        let ext = extensionOf(name)
        let stem = ext.isEmpty ? name : String(name.dropLast(ext.count + 1))
        var n = 1
        while true {
            let candidate = "\(stem) (\(n))" + (ext.isEmpty ? "" : ".\(ext)")
            if !existing.contains(candidate) { return candidate }
            n += 1
        }
    }

    /// Short human label for a file, e.g. "PDF document".
    public static func typeLabel(_ fileName: String, mimeType: String?) -> String {
        let ext = extensionOf(fileName)
        let mime = mimeType?.lowercased() ?? ""
        switch true {
        case ext == "pdf" || mime == "application/pdf": return "PDF document"
        case ["doc", "docx", "odt", "rtf", "pages"].contains(ext): return "Word document"
        case ["xls", "xlsx", "ods", "csv", "numbers"].contains(ext): return "Spreadsheet"
        case ["ppt", "pptx", "odp", "key"].contains(ext): return "Presentation"
        case ["zip", "rar", "7z", "tar", "gz"].contains(ext): return "Archive"
        case mime.hasPrefix("image/") || ["png", "jpg", "jpeg", "gif", "webp", "heic"].contains(ext): return "Image"
        case mime.hasPrefix("video/") || ["mp4", "mkv", "webm", "mov", "m4v"].contains(ext): return "Video"
        case mime.hasPrefix("audio/") || ["mp3", "m4a", "wav", "ogg", "aac"].contains(ext): return "Audio"
        case mime.hasPrefix("text/") || ext == "txt": return "Text file"
        case !ext.isEmpty: return ext.uppercased() + " file"
        default: return "File"
        }
    }
}
