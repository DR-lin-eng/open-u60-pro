import SwiftUI

enum DNSMode: Int, CaseIterable, Identifiable {
    case auto, custom, doh

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .auto: "自动"
        case .custom: "自定义"
        case .doh: "DoH"
        }
    }
}

@Observable
@MainActor
final class DNSSettingsViewModel {
    var config: DNSConfig = .empty
    var isLoading: Bool = false
    var message: String?
    var messageIsError: Bool = false

    // Mode
    var selectedMode: DNSMode = .auto

    // Editable fields
    var editPrimary: String = ""
    var editSecondary: String = ""

    // DoH
    var doh: DoHStatus = .empty
    var editUpstream: String = ""
    var cacheEntries: [DoHCacheEntry] = []
    var showCacheInspector: Bool = false

    private let client: AgentClient
    private let authManager: AuthManager
    private var initialLoadDone = false

    init(client: AgentClient, authManager: AuthManager) {
        self.client = client
        self.authManager = authManager
    }

    func refresh() async {
        isLoading = true
        message = nil

        do {
            let data = try await client.getJSON("/api/router/dns")
            config = DNSParser.parse(data)
            if !initialLoadDone {
                editPrimary = config.primaryDns
                editSecondary = config.secondaryDns
            }
        } catch {
            showMessage("加载 DNS 失败：\(error.localizedDescription)", isError: true)
        }

        // DoH status (independent, don't fail the whole refresh)
        do {
            let data = try await client.getJSON("/api/doh/status")
            doh = DoHParser.parse(data)
            if !initialLoadDone && editUpstream.isEmpty {
                editUpstream = doh.upstreamUrl
            }
            if doh.enabled {
                await refreshCache()
            }
        } catch {
            doh = .empty
            if message == nil {
                showMessage("DoH 状态不可用", isError: false)
            }
        }

        // Derive mode from state only on initial load
        if !initialLoadDone {
            if doh.enabled {
                selectedMode = .doh
            } else if config.isManual {
                selectedMode = .custom
            } else {
                selectedMode = .auto
            }
            initialLoadDone = true
        }

        isLoading = false
    }

    func apply() async {
        switch selectedMode {
        case .auto:
            await applyAuto()
        case .custom:
            await applyCustom()
        case .doh:
            await applyDoH()
        }
    }

    private func applyAuto() async {
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/dns", body: [
                "dns_mode": "auto",
                "prefer_dns_manual": "",
                "standby_dns_manual": ""
            ])
            // Always disable DoH (idempotent, prevents orphaned dnsmasq forwarding)
            let _ = try await client.postJSON("/api/doh/disable")
            showMessage("DNS 已设为自动", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
            isLoading = false
        }
    }

    private func isValidIPv4(_ str: String) -> Bool {
        let parts = str.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return false }
        return parts.allSatisfy { part in
            guard let n = UInt16(part) else { return false }
            return n <= 255
        }
    }

    private func applyCustom() async {
        guard !editPrimary.isEmpty else {
            showMessage("主 DNS 不能为空", isError: true)
            return
        }
        guard isValidIPv4(editPrimary) else {
            showMessage("主 DNS 地址无效", isError: true)
            return
        }
        if !editSecondary.isEmpty {
            guard isValidIPv4(editSecondary) else {
                showMessage("备用 DNS 地址无效", isError: true)
                return
            }
        }
        isLoading = true
        do {
            let _ = try await client.putJSON("/api/router/dns", body: [
                "dns_mode": "manual",
                "prefer_dns_manual": editPrimary,
                "standby_dns_manual": editSecondary
            ])
            // Always disable DoH (idempotent, prevents orphaned dnsmasq forwarding)
            let _ = try await client.postJSON("/api/doh/disable")
            showMessage("DNS 已设为自定义（\(editPrimary)）", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
            isLoading = false
        }
    }

    private func applyDoH() async {
        guard !editPrimary.isEmpty else {
            showMessage("主 DNS 不能为空", isError: true)
            return
        }
        guard isValidIPv4(editPrimary) else {
            showMessage("主 DNS 地址无效", isError: true)
            return
        }
        if !editSecondary.isEmpty {
            guard isValidIPv4(editSecondary) else {
                showMessage("备用 DNS 地址无效", isError: true)
                return
            }
        }
        guard !editUpstream.isEmpty else {
            showMessage("DoH 上游 URL 不能为空", isError: true)
            return
        }
        isLoading = true
        do {
            // Set manual DNS (DoH local resolver)
            let _ = try await client.putJSON("/api/router/dns", body: [
                "dns_mode": "manual",
                "prefer_dns_manual": editPrimary,
                "standby_dns_manual": editSecondary
            ])
            // Update DoH upstream
            let _ = try await client.putJSON("/api/doh/config", body: [
                "upstream_url": editUpstream
            ])
            // Enable DoH if not running
            if !doh.enabled {
                let _ = try await client.postJSON("/api/doh/enable")
            }
            showMessage("DoH 已启用：\(editUpstream)", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
            isLoading = false
        }
    }

    func clearCache() async {
        isLoading = true
        do {
            let _ = try await client.postJSON("/api/doh/cache/clear")
            showMessage("DNS 缓存已清空", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
            isLoading = false
        }
    }

    func refreshCache() async {
        do {
            let list = try await client.getJSONArray("/api/doh/cache")
            cacheEntries = DoHParser.parseCacheEntries(list)
        } catch {
            // cache fetch is best-effort
        }
    }

    func applyPreset(primary: String, secondary: String, upstream: String) {
        initialLoadDone = true
        if selectedMode == .auto {
            selectedMode = .custom
        }
        editPrimary = primary
        editSecondary = secondary
        editUpstream = upstream
    }

    private func showMessage(_ text: String, isError: Bool) {
        message = text
        messageIsError = isError
    }
}
