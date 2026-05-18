import SwiftUI

struct RouterSettingsListView: View {
    let client: AgentClient
    let authManager: AuthManager

    var body: some View {
        NavigationStack {
            List {
                Section("蜂窝网络") {
                    NavigationLink {
                        MobileNetworkView(viewModel: MobileNetworkViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("移动网络", systemImage: "cellularbars")
                    }

                    NavigationLink {
                        NetworkModeView(viewModel: NetworkModeViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("网络模式", systemImage: "antenna.radiowaves.left.and.right")
                    }

                    NavigationLink {
                        CellLockView(viewModel: CellLockViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("小区锁定", systemImage: "lock.fill")
                    }

                    NavigationLink {
                        STCView(viewModel: STCViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("智能基站连接", systemImage: "building.2")
                    }

                    NavigationLink {
                        SignalDetectView(viewModel: SignalDetectViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("信号检测", systemImage: "waveform.badge.magnifyingglass")
                    }

                    NavigationLink {
                        SIMView(viewModel: SIMViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("SIM 卡", systemImage: "simcard.2")
                    }

                    NavigationLink {
                        STKMenuView(viewModel: STKViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("SIM 服务", systemImage: "phone.badge.waveform")
                    }
                }

                Section("连接设置") {
                    NavigationLink {
                        WiFiSettingsView(viewModel: WiFiSettingsViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("Wi-Fi", systemImage: "wifi")
                    }

                    NavigationLink {
                        GuestWiFiSettingsView(viewModel: GuestWiFiSettingsViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("访客 Wi-Fi", systemImage: "wifi.exclamationmark")
                    }

                    NavigationLink {
                        APNView(viewModel: APNViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("APN", systemImage: "simcard")
                    }

                    NavigationLink {
                        LANSettingsView(viewModel: LANSettingsViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("局域网 / DHCP", systemImage: "network")
                    }

                    NavigationLink {
                        DNSSettingsView(viewModel: DNSSettingsViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("DNS", systemImage: "globe")
                    }
                }

                Section("安全") {
                    NavigationLink {
                        FirewallSettingsView(viewModel: FirewallSettingsViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("防火墙", systemImage: "flame")
                    }

                    NavigationLink {
                        TelemetryBlockerView(viewModel: TelemetryBlockerViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("遥测拦截", systemImage: "eye.slash")
                    }

                    NavigationLink {
                        VPNPassthroughView(viewModel: VPNPassthroughViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("VPN 透传", systemImage: "lock.shield")
                    }

                }

                Section("服务质量") {
                    NavigationLink {
                        QoSView(viewModel: QoSViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("QoS", systemImage: "speedometer")
                    }
                }

                Section("跟随系统") {
                    NavigationLink {
                        DeviceControlView(viewModel: DeviceControlViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("设备控制", systemImage: "power")
                    }

                }
            }
            .navigationTitle("路由")
        }
    }
}
