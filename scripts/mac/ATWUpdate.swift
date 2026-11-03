// Standalone macOS updater. Build with xcrun swiftc -O -parse-as-library.
// No game/account data is opened. Only the distribution .app can be replaced.
import Foundation
import CryptoKit
import Security
import LocalAuthentication
import Darwin

let repository = "alextyhwang/ATW-Client-Portable"
let bundleIdentifier = "com.alextyhwang.atw-client"
let keychainService = "com.alextyhwang.atw-client.github-updates"
let fm = FileManager.default

enum UpdateError: Error {
    case invalid, authRequired, offline, busy, readOnly, keychain
}

struct Version: Comparable, Equatable {
    let parts: [Int]
    init(_ text: String) throws {
        let fields = text.split(separator: ".", omittingEmptySubsequences: false)
        guard fields.count == 3, fields.allSatisfy({ !$0.isEmpty && $0.count <= 9 &&
            $0.allSatisfy({ $0.isASCII && $0.isNumber }) && ($0 == "0" || !$0.hasPrefix("0")) }),
            fields.allSatisfy({ Int($0) != nil }) else { throw invalid() }
        parts = fields.map { Int($0)! }
    }
    static func < (lhs: Self, rhs: Self) -> Bool { lhs.parts.lexicographicallyPrecedes(rhs.parts) }
}

struct Manifest: Codable {
    let schema: Int
    let version: String
    let channel: String
    let archive: String
    let sha256: String
    let bytes: Int64
    let packageID: String
    func validate() throws {
        _ = try Version(version)
        guard schema == 1, ["stable", "beta"].contains(channel),
              archive == "ATW-Client-Mac-arm64-\(version).zip",
              sha256.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil,
              packageID.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil,
              bytes > 0, bytes < 4_000_000_000 else { throw invalid() }
    }
}

struct Asset: Decodable {
    let id: Int64
    let name: String
    let size: Int64
}
struct Release: Decodable {
    let tag_name: String
    let draft: Bool
    let prerelease: Bool
    let assets: [Asset]
}
struct Journal: Codable {
    let scratch: String
    let oldVersion: String
    let newVersion: String
}
struct SeedEntry: Decodable {
    let path: String
    let sha256: String?
    let executable: Bool?
    let link: String?
}

func invalid(line: UInt = #line) -> UpdateError {
    #if ATW_UPDATE_DIAGNOSTICS
    FileHandle.standardError.write(Data("ATW updater fixture invalid at line \(line)\n".utf8))
    #endif
    return .invalid
}
func require(_ condition: Bool, line: UInt = #line) throws {
    if !condition {
        #if ATW_UPDATE_DIAGNOSTICS
        FileHandle.standardError.write(Data("ATW updater fixture assertion line \(line)\n".utf8))
        #endif
        throw UpdateError.invalid
    }
}
func json<T: Encodable>(_ object: T, to path: URL) throws {
    try JSONEncoder().encode(object).write(to: path, options: .atomic)
}
func load<T: Decodable>(_ type: T.Type, _ path: URL) throws -> T {
    try JSONDecoder().decode(type, from: Data(contentsOf: path))
}
func command(_ executable: String, _ arguments: [String], limit: Int = 16_000_000) throws -> Data {
    let task = Process()
    task.executableURL = URL(fileURLWithPath: executable)
    task.arguments = arguments
    let pipe = Pipe()
    task.standardOutput = pipe
    task.standardError = FileHandle.nullDevice
    try task.run()
    let output = pipe.fileHandleForReading.readDataToEndOfFile()
    task.waitUntilExit()
    try require(task.terminationStatus == 0 && output.count <= limit)
    return output
}
func digest(_ file: URL) throws -> String {
    let stream = try FileHandle(forReadingFrom: file)
    defer { try? stream.close() }
    var hash = SHA256()
    while let block = try stream.read(upToCount: 1024 * 1024), !block.isEmpty { hash.update(data: block) }
    return hash.finalize().map { String(format: "%02x", $0) }.joined()
}
func plainPath(_ path: URL) throws {
    // Reject symlink ancestors, including a symlinked application destination.
    // standardizedFileURL rewrites /private/var to the /var symlink on macOS.
    // Callers pass a canonical parent; preserve that spelling while inspecting.
    var current = path
    while current.path != "/" {
        let values = try? current.resourceValues(forKeys: [.isSymbolicLinkKey])
        #if ATW_UPDATE_DIAGNOSTICS
        if values?.isSymbolicLink == true {
            FileHandle.standardError.write(Data("Fixture linked ancestor: \(current.path)\n".utf8))
        }
        #endif
        // macOS exposes /var (and its /private spelling) as a system symlink;
        // rejecting it would make every temporary fixture and Application
        // Support path unusable. User-controlled descendants remain rejected.
        try require(values?.isSymbolicLink != true || current.path == "/var" || current.path == "/private/var")
        current.deleteLastPathComponent()
    }
}
func bundleInfo(_ app: URL) throws -> (version: String, packageID: String, channel: String) {
    try plainPath(app)
    let info = try PropertyListSerialization.propertyList(from: Data(contentsOf:
        app.appendingPathComponent("Contents/Info.plist")), format: nil) as? [String: Any]
    guard info?["CFBundleIdentifier"] as? String == bundleIdentifier,
          let version = info?["CFBundleShortVersionString"] as? String,
          let channel = info?["ATWUpdateChannel"] as? String,
          let executable = info?["CFBundleExecutable"] as? String,
          executable == "ATW Client", ["stable", "beta"].contains(channel) else { throw invalid() }
    _ = try Version(version)
    let resources = app.appendingPathComponent("Contents/Resources")
    let packageID = try String(contentsOf: resources.appendingPathComponent("package-id"), encoding: .utf8)
        .trimmingCharacters(in: .whitespacesAndNewlines)
    try require(packageID.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil)
    try require(fm.isExecutableFile(atPath: app.appendingPathComponent("Contents/MacOS/ATW Client").path))
    try require(fm.isExecutableFile(atPath: app.appendingPathComponent("Contents/MacOS/atw-update").path))
    return (version, packageID, channel)
}
func verifySeed(_ app: URL, packageID: String) throws {
    let resources = app.appendingPathComponent("Contents/Resources")
    var manifest = try Data(contentsOf: resources.appendingPathComponent("package-manifest.json"))
    if manifest.last == 10 { manifest.removeLast() }
    try require(SHA256.hash(data: manifest).map { String(format: "%02x", $0) }.joined() == packageID)
    let entries = try JSONDecoder().decode([SeedEntry].self, from: manifest)
    let package = resources.appendingPathComponent("package")
    let required = ["atw-launch.app/Contents/MacOS/atw-launch", "atw-config.app/Contents/MacOS/atw-config",
                    "runtime/java/bin/java", "config/settings.json"]
    for name in required {
        try require(entries.contains(where: { $0.path == name && $0.sha256 != nil }))
        if name != "config/settings.json" { try require(fm.isExecutableFile(atPath: package.appendingPathComponent(name).path)) }
    }
    for entry in entries {
        try require(!entry.path.hasPrefix("/") && !entry.path.contains("\\") &&
                    !entry.path.split(separator: "/").contains(".."))
        let path = package.appendingPathComponent(entry.path)
        if let link = entry.link {
            try require(try fm.destinationOfSymbolicLink(atPath: path.path) == link)
            try require(path.resolvingSymlinksInPath().path.hasPrefix(package.path + "/"))
        } else if let hash = entry.sha256 {
            try plainPath(path)
            try require(try digest(path) == hash)
            try require(fm.isExecutableFile(atPath: path.path) == entry.executable)
        } else { throw invalid() }
    }
}

func keychainToken() throws -> String? {
    let context = LAContext()
    context.interactionNotAllowed = true
    let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: keychainService, kSecAttrAccount as String: repository,
        kSecReturnData as String: true, kSecUseAuthenticationContext as String: context]
    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)
    if status == errSecItemNotFound { return nil }
    guard status == errSecSuccess, let bytes = item as? Data,
          let token = String(data: bytes, encoding: .utf8) else { throw UpdateError.keychain }
    return token
}
func saveToken(_ token: String) throws {
    try require(!token.isEmpty && token.count < 4096 && !token.contains(where: { $0.isWhitespace }))
    let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: keychainService, kSecAttrAccount as String: repository]
    let attributes: [String: Any] = [kSecValueData as String: Data(token.utf8)]
    var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
    if status == errSecItemNotFound {
        var insertion = query.merging(attributes) { _, new in new }
        insertion[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        status = SecItemAdd(insertion as CFDictionary, nil)
    }
    guard status == errSecSuccess else { throw UpdateError.keychain }
}

// Never forward the GitHub credential to signed CDN redirect destinations.
final class RedirectPolicy: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        guard let url = request.url, allowedRemote(url) else { completionHandler(nil); return }
        var safe = request
        if url.host != "api.github.com" { safe.setValue(nil, forHTTPHeaderField: "Authorization") }
        completionHandler(safe)
    }
}
func allowedRemote(_ url: URL) -> Bool {
    url.scheme == "https" && url.user == nil && url.password == nil &&
    ["api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
     "github.com"].contains(url.host ?? "") && (url.port == nil || url.port == 443)
}
struct Feed {
    let token: String?
    let fixture: URL?
    func request(_ path: String, binary: Bool = false) throws -> URLRequest {
        let root = fixture ?? URL(string: "https://api.github.com/repos/\(repository)/")!
        guard let url = URL(string: path, relativeTo: root)?.absoluteURL else { throw invalid() }
        if fixture == nil { try require(allowedRemote(url)) }
        var request = URLRequest(url: url)
        request.timeoutInterval = binary ? 300 : 12
        request.setValue("ATW-Client-Mac-Updater/1", forHTTPHeaderField: "User-Agent")
        request.setValue(binary ? "application/octet-stream" : "application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("2022-11-28", forHTTPHeaderField: "X-GitHub-Api-Version")
        if let token, fixture == nil { request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization") }
        return request
    }
    func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForResource = 600
        config.httpCookieStorage = nil
        config.urlCredentialStorage = nil
        return URLSession(configuration: config, delegate: RedirectPolicy(), delegateQueue: nil)
    }
    func response(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { throw UpdateError.offline }
        if [401, 403, 404].contains(http.statusCode) { throw UpdateError.authRequired }
        guard http.statusCode == 200 else { throw UpdateError.offline }
    }
    func data(_ path: String, binary: Bool = false) async throws -> Data {
        let client = session()
        defer { client.invalidateAndCancel() }
        let (bytes, reply) = try await client.data(for: request(path, binary: binary))
        try response(reply)
        try require(bytes.count < 2_000_000)
        return bytes
    }
    func download(_ asset: Asset, to destination: URL) async throws {
        let client = session()
        defer { client.invalidateAndCancel() }
        let (temporary, reply) = try await client.download(for: request("releases/assets/\(asset.id)", binary: true))
        try response(reply)
        let size = try temporary.resourceValues(forKeys: [.fileSizeKey]).fileSize
        try require(size == Int(asset.size))
        try fm.moveItem(at: temporary, to: destination)
    }
    func candidate(channel: String, current: String) async throws -> (Manifest, Asset)? {
        let releases = try JSONDecoder().decode([Release].self, from: await data("releases?per_page=100"))
        let prefix = "mac-\(channel)-v"
        let eligible = releases.filter { !$0.draft && (channel == "beta" || !$0.prerelease) && $0.tag_name.hasPrefix(prefix) }
            .compactMap { release -> (Release, Version)? in
                guard let version = try? Version(String(release.tag_name.dropFirst(prefix.count))) else { return nil }
                return (release, version)
            }.sorted { $0.1 > $1.1 }
        guard let selected = eligible.first, selected.1 > (try Version(current)) else { return nil }
        let release = selected.0
        guard let manifestAsset = release.assets.first(where: { $0.name == "atw-mac-\(channel).json" }),
              manifestAsset.size > 0, manifestAsset.size < 65536 else { throw invalid() }
        let manifest = try JSONDecoder().decode(Manifest.self, from: await data("releases/assets/\(manifestAsset.id)", binary: true))
        try manifest.validate()
        try require(manifest.channel == channel && release.tag_name == prefix + manifest.version)
        guard let archive = release.assets.first(where: { $0.name == manifest.archive }),
              archive.id > 0, archive.size == manifest.bytes else { throw invalid() }
        return (manifest, archive)
    }
}

// Parse the central directory before ditto can create files. Relative framework
// links are allowed; traversal, link descendants, duplicates and ZIP64 are not.
func zipEntries(_ zip: URL) throws -> [(String, Bool)] {
    let stream = try FileHandle(forReadingFrom: zip)
    defer { try? stream.close() }
    let length = try stream.seekToEnd()
    try require(length >= 22 && length < 4_000_000_000)
    let tailSize = min(Int(length), 65557)
    try stream.seek(toOffset: length - UInt64(tailSize))
    let tail = try stream.read(upToCount: tailSize) ?? Data()
    func number(_ bytes: Data, _ offset: Int, _ count: Int) throws -> Int {
        try require(offset >= 0 && offset + count <= bytes.count)
        return (0..<count).reduce(0) { $0 | (Int(bytes[offset + $1]) << ($1 * 8)) }
    }
    guard let end = (0...(tail.count - 22)).reversed().first(where: {
        (try? number(tail, $0, 4)) == 0x06054b50 &&
        (try? number(tail, $0 + 20, 2)) == tail.count - $0 - 22
    }) else { throw invalid() }
    let count = try number(tail, end + 10, 2)
    let size = try number(tail, end + 12, 4)
    let offset = try number(tail, end + 16, 4)
    try require(count > 0 && count < 65535 && size < 32_000_000 &&
                offset + size == Int(length) - tailSize + end &&
                (try number(tail, end + 4, 4)) == 0 && (try number(tail, end + 8, 2)) == count)
    try stream.seek(toOffset: UInt64(offset))
    let central = try stream.read(upToCount: size) ?? Data()
    var cursor = 0
    var result: [(String, Bool)] = []
    var names = Set<String>()
    for _ in 0..<count {
        try require((try number(central, cursor, 4)) == 0x02014b50)
        let nameSize = try number(central, cursor + 28, 2)
        let extraSize = try number(central, cursor + 30, 2)
        let commentSize = try number(central, cursor + 32, 2)
        let endName = cursor + 46 + nameSize
        try require(endName + extraSize + commentSize <= central.count)
        guard let name = String(data: central[(cursor + 46)..<endName], encoding: .utf8) else { throw invalid() }
        let components = name.split(separator: "/", omittingEmptySubsequences: false)
        try require(!name.isEmpty && !name.hasPrefix("/") && !name.contains("\\") &&
                    !name.contains(where: { $0.asciiValue.map { $0 < 32 } ?? false }) &&
                    !components.contains("..") && !components.contains(".") &&
                    !components.dropLast().contains("") &&
                    (components.first == "ATW Client.app" || components.first == "__MACOSX"))
        let normalized = name.precomposedStringWithCanonicalMapping.lowercased()
        try require(names.insert(normalized).inserted)
        let mode = try number(central, cursor + 38, 4) >> 16
        let kind = mode & 0o170000
        try require([0, 0o100000, 0o040000, 0o120000].contains(kind))
        result.append((name, kind == 0o120000))
        cursor = endName + extraSize + commentSize
    }
    try require(cursor == central.count)
    for (name, link) in result where link {
        try require(!name.hasPrefix("__MACOSX/") && !result.contains(where: { $0.0.hasPrefix(name + "/") }))
        let targetData = try command("/usr/bin/unzip", ["-p", zip.path, name], limit: 4096)
        guard let target = String(data: targetData, encoding: .utf8), !target.isEmpty,
              !target.hasPrefix("/"), !target.contains("\\"), !target.contains("\0") else { throw invalid() }
        let root = URL(fileURLWithPath: "/ATW Client.app")
        let resolved = URL(fileURLWithPath: "/" + name).deletingLastPathComponent().appendingPathComponent(target).standardizedFileURL
        try require(resolved.path.hasPrefix(root.path + "/"))
    }
    return result
}

func packageBusy(_ base: URL) throws -> Bool {
    let commands = try command("/bin/ps", ["-axo", "command="])
    let prefix = base.appendingPathComponent("package").path + "/"
    return String(decoding: commands, as: UTF8.self).split(separator: "\n").contains { $0.contains(prefix) }
}

struct Installation {
    let app: URL
    let base: URL
    var journal: URL { app.deletingLastPathComponent().appendingPathComponent(".\(app.lastPathComponent).atw-update.json") }
    var updates: URL { base.appendingPathComponent("updates") }
    var failed: URL { updates.appendingPathComponent("failed-version") }
    func swap(_ other: URL) throws {
        try plainPath(app)
        try plainPath(other)
        guard renamex_np(app.path, other.path, UInt32(RENAME_SWAP)) == 0 else { throw UpdateError.readOnly }
    }
    func transaction() throws -> (Journal, URL)? {
        guard fm.fileExists(atPath: journal.path) else { return nil }
        try plainPath(journal)
        let entry = try load(Journal.self, journal)
        try require(entry.scratch.range(of: "^\\.atw-update-[A-Fa-f0-9-]{36}$", options: .regularExpression) != nil)
        _ = try Version(entry.oldVersion)
        _ = try Version(entry.newVersion)
        let directory = app.deletingLastPathComponent().appendingPathComponent(entry.scratch)
        try plainPath(directory)
        return (entry, directory)
    }
    func recover(resume: Bool, acknowledge: Bool) throws -> Bool {
        guard let (entry, directory) = try transaction() else { return false }
        let backup = directory.appendingPathComponent("ATW Client.app")
        let current = try bundleInfo(app)
        if acknowledge {
            try require(current.version == entry.newVersion)
        } else if !resume && current.version == entry.newVersion {
            try require(try bundleInfo(backup).version == entry.oldVersion)
            try swap(backup)
            try fm.createDirectory(at: updates, withIntermediateDirectories: true)
            try entry.newVersion.write(to: failed, atomically: true, encoding: .utf8)
        } else if resume {
            try require(current.version == entry.newVersion)
            return false
        }
        try fm.removeItem(at: journal)
        try fm.removeItem(at: directory)
        return !acknowledge && current.version == entry.newVersion
    }
    func apply(_ manifest: Manifest, archive: URL) throws {
        let current = try bundleInfo(app)
        try require(try Version(manifest.version) > Version(current.version))
        try require(try digest(archive) == manifest.sha256)
        _ = try zipEntries(archive)
        try plainPath(app.deletingLastPathComponent())
        let directory = app.deletingLastPathComponent().appendingPathComponent(".atw-update-" + UUID().uuidString)
        try fm.createDirectory(at: directory, withIntermediateDirectories: false,
                               attributes: [.posixPermissions: 0o700])
        var journalWritten = false
        defer { if !journalWritten { try? fm.removeItem(at: directory) } }
        _ = try command("/usr/bin/ditto", ["-x", "-k", archive.path, directory.path])
        let candidate = directory.appendingPathComponent("ATW Client.app")
        let incoming = try bundleInfo(candidate)
        try require(incoming.version == manifest.version && incoming.packageID == manifest.packageID && incoming.channel == manifest.channel)
        try verifySeed(candidate, packageID: incoming.packageID)
        // Structural admission, not a claim of Developer ID/notarization trust.
        let updater = candidate.appendingPathComponent("Contents/MacOS/atw-update")
        let slices = try command("/usr/bin/lipo", ["-archs", updater.path])
        try require(String(decoding: slices, as: UTF8.self).split(separator: " ").contains(where: { $0.trimmingCharacters(in: .whitespacesAndNewlines) == "arm64" }))
        // Recheck after the network/extraction window; detached games can outlive Config.
        if try packageBusy(base) { throw UpdateError.busy }
        try json(Journal(scratch: directory.lastPathComponent, oldVersion: current.version,
                         newVersion: manifest.version), to: journal)
        journalWritten = true
        try swap(candidate) // one atomic filesystem operation, no missing-app window
    }
}

struct Options {
    let values: [String]
    func has(_ flag: String) -> Bool { values.contains(flag) }
    func value(_ flag: String) -> String? {
        guard let index = values.firstIndex(of: flag), index + 1 < values.count else { return nil }
        return values[index + 1]
    }
}
func status(_ name: String, version: String? = nil, base: URL?) {
    var value = ["status": name, "repository": repository]
    if let version { value["version"] = version }
    if let data = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]) {
        print(String(decoding: data, as: UTF8.self))
        if let base {
            let directory = base.appendingPathComponent("updates")
            try? fm.createDirectory(at: directory, withIntermediateDirectories: true)
            try? data.write(to: directory.appendingPathComponent("status.json"), options: .atomic)
        }
    }
}

#if !ATW_UPDATE_TESTS
@main struct ATWUpdate {
    static func main() async {
        umask(0o077)
        let options = Options(values: Array(CommandLine.arguments.dropFirst()))
        let base = URL(fileURLWithPath: options.value("--base") ??
            (NSHomeDirectory() + "/Library/Application Support/ATW Client"))
        var packageSafe = false
        do {
            if options.has("--import-github-token-stdin") {
                let data = FileHandle.standardInput.readDataToEndOfFile()
                guard data.count < 4096, let text = String(data: data, encoding: .utf8) else { throw invalid() }
                try saveToken(text.trimmingCharacters(in: .whitespacesAndNewlines))
                status("credential-saved", base: nil)
                return
            }
            guard let appPath = options.value("--app") else { throw invalid() }
            let requested = URL(fileURLWithPath: appPath).standardizedFileURL
            let app = requested.deletingLastPathComponent().resolvingSymlinksInPath().appendingPathComponent(requested.lastPathComponent)
            let install = Installation(app: app, base: base)
            let info = try bundleInfo(app)
            let check = options.has("--check-only")
            // The outer bootstrap owns lockf for this entire helper/UI/game lifetime.
            if !check {
                try require(options.has("--launch-lock-held"))
                if try packageBusy(base) { status("busy", base: base); exit(20) }
                packageSafe = true
                if options.has("--guard-package") { return }
                let rolledBack = try install.recover(resume: options.has("--resume-update"),
                                                     acknowledge: options.has("--acknowledge"))
                if rolledBack { status("rolled-back", base: base); exit(10) }
                if options.has("--acknowledge") { return }
                if options.has("--resume-update") { return }
            }
            var fixture: URL?
            if let fixtureText = options.value("--test-feed") {
                // No ambient env/config override can redirect a production credential.
                // Foundation drops /private from /private/var only for paths that exist,
                // so a not-yet-created base would otherwise be spelled differently.
                func systemSpelling(_ path: String) -> String {
                    ["/private/var/", "/private/tmp/", "/private/etc/"].contains(where: path.hasPrefix)
                        ? String(path.dropFirst("/private".count)) : path
                }
                let temporary = systemSpelling(URL(fileURLWithPath: NSTemporaryDirectory())
                    .resolvingSymlinksInPath().path + "/")
                guard options.has("--test-mode"), systemSpelling(app.path).hasPrefix(temporary),
                      systemSpelling(base.resolvingSymlinksInPath().path).hasPrefix(temporary),
                      let url = URL(string: fixtureText), ["127.0.0.1", "localhost"].contains(url.host ?? ""),
                      url.scheme == "http" else { throw invalid() }
                fixture = url
            }
            let token = fixture == nil ? try keychainToken() : nil
            if token == nil && fixture == nil { throw UpdateError.authRequired }
            let feed = Feed(token: token, fixture: fixture)
            guard let (manifest, asset) = try await feed.candidate(channel: info.channel, current: info.version) else {
                status("current", version: info.version, base: base); return
            }
            if check { status("available", version: manifest.version, base: base); return }
            if (try? String(contentsOf: install.failed, encoding: .utf8)) == manifest.version {
                status("previous-update-failed", version: manifest.version, base: base); return
            }
            if try packageBusy(base) { status("busy", base: base); return }
            try fm.createDirectory(at: install.updates, withIntermediateDirectories: true)
            let archive = install.updates.appendingPathComponent(UUID().uuidString + ".zip")
            defer { try? fm.removeItem(at: archive) }
            status("downloading", version: manifest.version, base: base)
            try await feed.download(asset, to: archive)
            try install.apply(manifest, archive: archive)
            // exit() below skips the defer above, which would leave the archive behind.
            try? fm.removeItem(at: archive)
            status("updated", version: manifest.version, base: base)
            exit(10) // bootstrap re-execs the replacement under the same live lock
        } catch {
            let name: String
            switch error {
            case UpdateError.authRequired: name = "auth-required"
            case UpdateError.keychain: name = "credential-unavailable"
            case UpdateError.busy: name = "busy"
            case UpdateError.readOnly: name = "read-only"
            case UpdateError.invalid: name = "invalid-update"
            case UpdateError.offline: name = "offline"
            case is URLError: name = "offline"
            case is DecodingError: name = "invalid-update"
            default:
                let failure = error as NSError
                name = failure.domain == NSCocoaErrorDomain &&
                    [NSFileWriteNoPermissionError, NSFileWriteVolumeReadOnlyError].contains(failure.code)
                    ? "read-only" : "installation-failed"
            }
            // Never log URLSession errors: signed asset URLs and headers are private.
            // Fixture builds only (isolated tests, no credentials) name the failure.
            #if ATW_UPDATE_DIAGNOSTICS
            FileHandle.standardError.write(Data("ATW updater fixture error: \(error)\n".utf8))
            #endif
            status(name, base: base)
            if options.has("--import-github-token-stdin") { exit(1) }
            if case UpdateError.busy = error { exit(20) }
            if !options.has("--check-only") && !packageSafe { exit(20) }
            exit(0) // a failed check must not prevent using the installed version
        }
    }
}
#endif
