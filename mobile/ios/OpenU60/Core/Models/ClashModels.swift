import Foundation

struct ClashStatus: Equatable {
    var running: Bool
    var version: String
    var mode: String
    var controllerPort: Int
    var ui: String
    var mixedPort: Int
    var redirPort: Int
    var tproxyPort: Int
    var allowLan: Bool
    var tunEnabled: Bool
    var connections: Int
    var downloadTotal: UInt64
    var uploadTotal: UInt64

    static let empty = ClashStatus(
        running: false,
        version: "",
        mode: "",
        controllerPort: 7788,
        ui: "",
        mixedPort: 0,
        redirPort: 0,
        tproxyPort: 0,
        allowLan: false,
        tunEnabled: false,
        connections: 0,
        downloadTotal: 0,
        uploadTotal: 0
    )

    var modeLabel: String {
        switch mode.lowercased() {
        case "rule": return "规则"
        case "global": return "全局"
        case "direct": return "直连"
        default: return "--"
        }
    }
}

struct ClashSelectorGroup: Equatable, Identifiable {
    let id: String
    var name: String
    var current: String
    var options: [String]
    var alive: Bool
}

enum ClashParser {
    static func offlineStatus() -> ClashStatus { .empty }

    static func parseStatus(_ version: [String: Any], _ configs: [String: Any], _ connections: [String: Any]) -> ClashStatus {
        ClashStatus(
            running: true,
            version: version["version"] as? String ?? "",
            mode: configs["mode"] as? String ?? "",
            controllerPort: 7788,
            ui: "zashboard",
            mixedPort: asInt(configs["mixed-port"]) ?? 0,
            redirPort: asInt(configs["redir-port"]) ?? 0,
            tproxyPort: asInt(configs["tproxy-port"]) ?? 0,
            allowLan: asBool(configs["allow-lan"]),
            tunEnabled: asBool((configs["tun"] as? [String: Any])?["enable"]),
            connections: (connections["connections"] as? [[String: Any]])?.count ?? 0,
            downloadTotal: asUInt64(connections["downloadTotal"]) ?? 0,
            uploadTotal: asUInt64(connections["uploadTotal"]) ?? 0
        )
    }

    static func parseSelectors(_ data: [String: Any]) -> [ClashSelectorGroup] {
        guard let proxies = data["proxies"] as? [String: [String: Any]] else { return [] }
        return proxies.compactMap { name, item in
            guard item["type"] as? String == "Selector" else { return nil }
            let options = item["all"] as? [String] ?? []
            guard !options.isEmpty else { return nil }
            return ClashSelectorGroup(
                id: name,
                name: name,
                current: item["now"] as? String ?? "",
                options: options,
                alive: asBool(item["alive"])
            )
        }
    }

    private static func asBool(_ value: Any?) -> Bool {
        if let str = value as? String {
            return str == "1" || str.lowercased() == "true" || str.lowercased() == "on"
        }
        if let num = value as? Int { return num != 0 }
        if let num = value as? NSNumber { return num.intValue != 0 }
        if let b = value as? Bool { return b }
        return false
    }

    private static func asInt(_ value: Any?) -> Int? {
        if let int = value as? Int { return int }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string) }
        return nil
    }

    private static func asUInt64(_ value: Any?) -> UInt64? {
        if let int = value as? UInt64 { return int }
        if let number = value as? NSNumber { return number.uint64Value }
        if let string = value as? String { return UInt64(string) }
        return nil
    }
}
