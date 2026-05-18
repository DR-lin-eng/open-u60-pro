import SwiftUI

@Observable
@MainActor
final class FirewallSettingsViewModel {
    var config: FirewallConfig = .empty
    var portForwardRules: [PortForwardRule] = []
    var filterRules: [FilterRule] = []
    var upnpEnabled: Bool = false
    var isLoading: Bool = false
    var message: String?
    var messageIsError: Bool = false
    var showAddPortForward: Bool = false

    // DMZ edit fields
    var editDmzEnabled: Bool = false
    var editDmzIP: String = ""

    private let client: AgentClient
    private let authManager: AuthManager

    init(client: AgentClient, authManager: AuthManager) {
        self.client = client
        self.authManager = authManager
    }

    func refresh() async {
        isLoading = true
        message = nil

        do {
            let data = try await client.getJSON("/api/router/firewall")
            config = FirewallParser.parseConfig(data)
            editDmzEnabled = config.dmzEnabled
            editDmzIP = config.dmzHost

            async let pfResult = fetchPortForwardRules()
            async let filterResult = fetchFilterRules()
            async let upnpResult = fetchUPnP()

            portForwardRules = await pfResult
            filterRules = await filterResult
            upnpEnabled = await upnpResult
        } catch {
            showMessage("加载防火墙设置失败：\(error.localizedDescription)", isError: true)
        }

        isLoading = false
    }

    private func fetchPortForwardRules() async -> [PortForwardRule] {
        do {
            let data = try await client.getJSON("/api/router/firewall/port-forward")
            return FirewallParser.parsePortForwardRules(data)
        } catch {
            return []
        }
    }

    private func fetchFilterRules() async -> [FilterRule] {
        do {
            let data = try await client.getJSON("/api/router/firewall/filter-rules")
            return FirewallParser.parseFilterRules(data)
        } catch {
            return []
        }
    }

    private func fetchUPnP() async -> Bool {
        do {
            let data = try await client.getJSON("/api/router/firewall/upnp")
            if let str = data["upnp_switch"] as? String {
                return str == "1"
            }
            return false
        } catch {
            return false
        }
    }

    func toggleFirewall(enabled: Bool) async {
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/firewall/switch", body: ["firewall_switch": enabled ? "1" : "0"])
            showMessage(enabled ? "防火墙已启用" : "防火墙已禁用", isError: false)
            config.enabled = enabled
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func setLevel(_ level: String) async {
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/firewall/level", body: ["firewall_level": level])
            showMessage("防火墙级别已设为 \(level)", isError: false)
            config.level = level
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func toggleNAT(enabled: Bool) async {
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/firewall/nat", body: ["nat_switch": enabled ? "1" : "0"])
            showMessage(enabled ? "NAT 已启用" : "NAT 已禁用", isError: false)
            config.nat = enabled
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func toggleUPnP(enabled: Bool) async {
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/firewall/upnp", body: ["upnp_switch": enabled ? "1" : "0"])
            showMessage(enabled ? "UPnP 已启用" : "UPnP 已禁用", isError: false)
            upnpEnabled = enabled
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func applyDMZ() async {
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/firewall/dmz", body: [
                "dmz_enabled": editDmzEnabled ? "1" : "0",
                "dmz_ip": editDmzIP
            ])
            showMessage("DMZ 设置已更新", isError: false)
            config.dmzEnabled = editDmzEnabled
            config.dmzHost = editDmzIP
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func togglePortForward(enabled: Bool) async {
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/firewall/port-forward/switch", body: ["port_forward_switch": enabled ? "1" : "0"])
            showMessage(enabled ? "端口转发已启用" : "端口转发已禁用", isError: false)
            config.portForwardEnabled = enabled
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func addPortForward(name: String, protocol_: String, wanPort: String, lanIP: String, lanPort: String) async {
        isLoading = true
        do {
            let _ = try await client.postJSON("/api/router/firewall/port-forward", body: [
                "action": "add",
                "name": name,
                "protocol": protocol_,
                "wan_port": wanPort,
                "lan_ip": lanIP,
                "lan_port": lanPort,
                "enabled": "1"
            ])
            showAddPortForward = false
            showMessage("端口转发规则已添加", isError: false)
            portForwardRules.append(PortForwardRule(id: UUID().uuidString, name: name, protocol_: protocol_, wanPort: wanPort, lanIP: lanIP, lanPort: lanPort, enabled: true))
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func deletePortForward(_ rule: PortForwardRule) async {
        isLoading = true
        do {
            let _ = try await client.postJSON("/api/router/firewall/port-forward", body: [
                "action": "delete",
                "id": rule.id
            ])
            showMessage("端口转发规则已删除", isError: false)
            portForwardRules.removeAll { $0.id == rule.id }
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    private func showMessage(_ text: String, isError: Bool) {
        message = text
        messageIsError = isError
    }
}
