import Foundation

@Observable
@MainActor
final class ClashViewModel {
    var status: ClashStatus = .empty
    var selectors: [ClashSelectorGroup] = []
    var isLoading: Bool = false
    var message: String?
    var messageIsError: Bool = false

    private let client: AgentClient
    private let clashClient = ClashDirectClient()

    init(client: AgentClient, authManager: AuthManager) {
        self.client = client
    }

    func refresh() async {
        isLoading = true
        message = nil
        do {
            let version = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/version")
            let configs = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/configs")
            let connections = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/connections")
            let proxies = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/proxies")
            status = ClashParser.parseStatus(version, configs, connections)
            selectors = ClashParser.parseSelectors(proxies)
        } catch {
            status = ClashParser.offlineStatus()
            selectors = []
            showMessage("加载 Clash 失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func setMode(_ mode: String) async {
        isLoading = true
        message = nil
        do {
            let _ = try await clashClient.patchJSON(agentBaseURL: client.baseURL, path: "/configs", body: ["mode": mode])
            let version = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/version")
            let configs = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/configs")
            let connections = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/connections")
            status = ClashParser.parseStatus(version, configs, connections)
            showMessage("模式已切换为 \(status.modeLabel)", isError: false)
        } catch {
            showMessage("切换模式失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    func select(group: String, option: String) async {
        isLoading = true
        message = nil
        do {
            let encodedGroup = group.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? group
            let _ = try await clashClient.putJSON(agentBaseURL: client.baseURL, path: "/proxies/\(encodedGroup)", body: [
                "name": option
            ])
            let version = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/version")
            let configs = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/configs")
            let connections = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/connections")
            let proxies = try await clashClient.getJSON(agentBaseURL: client.baseURL, path: "/proxies")
            status = ClashParser.parseStatus(version, configs, connections)
            selectors = ClashParser.parseSelectors(proxies)
            showMessage("已切换 \(group) 到 \(option)", isError: false)
        } catch {
            showMessage("切换节点失败：\(error.localizedDescription)", isError: true)
        }
        isLoading = false
    }

    private func showMessage(_ text: String, isError: Bool) {
        message = text
        messageIsError = isError
    }
}
