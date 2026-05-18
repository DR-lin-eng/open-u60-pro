import SwiftUI

struct ToolsListView: View {
    let client: AgentClient
    let authManager: AuthManager

    var body: some View {
        NavigationStack {
            List {
                Section("自动化") {
                    NavigationLink {
                        SchedulerView(viewModel: SchedulerViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("自动化任务", systemImage: "clock.arrow.2.circlepath")
                    }
                    NavigationLink {
                        SMSForwardConfigView(viewModel: SMSForwardViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("短信转发", systemImage: "envelope.arrow.triangle.branch")
                    }
                }

                Section("网络工具") {
                    NavigationLink {
                        SpeedTestView(viewModel: SpeedTestViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("速度测试", systemImage: "speedometer")
                    }

                    NavigationLink {
                        LANSpeedTestView(viewModel: LANSpeedTestViewModel(client: client))
                    } label: {
                        Label("局域网测速", systemImage: "wifi")
                    }

                    NavigationLink {
                        EnableADBView(client: client, authManager: authManager)
                    } label: {
                        Label("启用 ADB", systemImage: "cable.connector.horizontal")
                    }

                    NavigationLink {
                        USBModeView(viewModel: USBConnectionViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("USB 模式", systemImage: "cable.connector")
                    }

                    NavigationLink {
                        BandLockView(viewModel: BandLockViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("频段锁定", systemImage: "lock.fill")
                    }

                    NavigationLink {
                        ATTerminalView(viewModel: ATTerminalViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("AT 终端", systemImage: "terminal.fill")
                    }

                    NavigationLink {
                        DeviceInfoView(viewModel: DeviceInfoViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("设备信息", systemImage: "info.circle")
                    }

                    NavigationLink {
                        ClientsView(viewModel: ClientsViewModel(client: client, authManager: authManager))
                    } label: {
                        Label("已连接设备", systemImage: "laptopcomputer.and.iphone")
                    }
                }

                Section("配置工具") {
                    NavigationLink {
                        ConfigToolView()
                    } label: {
                        Label("配置解密/加密", systemImage: "doc.badge.gearshape")
                    }
                }

                Section("需要 Shell 访问") {
                    NavigationLink {
                        PlaceholderView(title: "TTL 设置", icon: "number", description: "通过 iptables 设置 TTL 覆盖，需要 shell 访问权限。")
                    } label: {
                        Label("TTL 设置", systemImage: "number")
                    }

                    NavigationLink {
                        PlaceholderView(title: "启用 SSH", icon: "terminal", description: "安装并启动 dropbear SSH 服务，需要 ADB USB 连接。")
                    } label: {
                        Label("启用 SSH", systemImage: "terminal")
                    }

                    NavigationLink {
                        PlaceholderView(title: "设备浏览器", icon: "folder", description: "浏览文件系统并采集设备信息，需要 ADB USB 连接。")
                    } label: {
                        Label("设备浏览器", systemImage: "folder")
                    }
                }
            }
            .navigationTitle("工具")
        }
    }
}
