import SwiftUI

struct CPUCardView: View {
    let systemInfo: SystemInfo
    let thermal: ThermalStatus

    var body: some View {
        CardView {
            VStack(spacing: 8) {
                Image(systemName: "cpu")
                    .font(.title2)
                    .foregroundStyle(
                        systemInfo.cpuUsagePercent > 0
                            ? Color.cpuUsageColor(systemInfo.cpuUsagePercent)
                            : (thermal.cpuTemp > 70 ? .red : .orange)
                    )
                if systemInfo.cpuUsagePercent > 0 {
                    AnimatedNumber(value: systemInfo.cpuUsagePercent, decimalPlaces: 0,
                                   font: .title3.weight(.bold),
                                   textColor: Color.cpuUsageColor(systemInfo.cpuUsagePercent),
                                   suffix: "%")
                } else {
                    Text("--")
                        .font(.title3.monospacedDigit().bold())
                        .foregroundStyle(.secondary)
                }
                AnimatedNumber(value: thermal.cpuTemp, decimalPlaces: 0,
                               font: .caption, textColor: .secondary, suffix: "\u{00B0}C")
                Text("CPU")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

struct CPUDetailSheet: View {
    let systemInfo: SystemInfo
    let thermal: ThermalStatus
    let client: AgentClient

    @State private var showProcessList = false
    @State private var bloatSummary: String = ""

    var body: some View {
        NavigationStack {
            List {
                row("CPU 占用", icon: "cpu", value: systemInfo.cpuUsagePercent > 0 ? String(format: "%.0f%%", systemInfo.cpuUsagePercent) : "—")
                if systemInfo.cpuUsagePercent > 0 && systemInfo.cpuUsageIsEstimate {
                    row("数据来源", icon: "info.circle", value: "估算")
                }
                row("CPU 核心数", icon: "square.grid.2x2", value: "\(systemInfo.cpuCores)")
                row("温度", icon: "thermometer.medium", value: String(format: "%.1f \u{00B0}C", thermal.cpuTemp))
                row("运行时长", icon: "clock", value: formatUptime(systemInfo.uptime))
                row("总内存", icon: "memorychip", value: formatBytes(systemInfo.memTotal))
                row("可用内存", icon: "memorychip", value: formatBytes(systemInfo.memFree))
                if systemInfo.memTotal > 0 {
                    let used = Double(systemInfo.memTotal - systemInfo.memFree) / Double(systemInfo.memTotal) * 100
                    row("内存占用", icon: "chart.bar", value: String(format: "%.0f%%", used))
                }

                Button {
                    showProcessList = true
                } label: {
                    HStack {
                        Label("进程", systemImage: "list.number")
                        Spacer()
                        if !bloatSummary.isEmpty {
                            Text(bloatSummary)
                                .foregroundStyle(.orange)
                                .font(.caption)
                        }
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .foregroundStyle(.primary)
            }
            .navigationTitle("CPU 与内存")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showProcessList) {
                ProcessListSheet(client: client)
            }
            .task {
                await loadBloatSummary()
            }
        }
    }

    private func loadBloatSummary() async {
        do {
            let result: ProcessListResponse = try await client.get("/api/system/top")
            if result.bloatCount > 0 {
                bloatSummary = "\(result.bloatCount) 个高占用进程 \(String(format: "%.0f", result.bloatCpuPct))%"
            }
        } catch {
            // Silently ignore — summary is optional
        }
    }

    private func row(_ label: String, icon: String, value: String) -> some View {
        HStack {
            Label(label, systemImage: icon)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
    }

    private func formatUptime(_ seconds: Int) -> String {
        let d = seconds / 86400
        let h = (seconds % 86400) / 3600
        let m = (seconds % 3600) / 60
        if d > 0 { return "\(d)天 \(h)小时 \(m)分" }
        if h > 0 { return "\(h)小时 \(m)分" }
        return "\(m)分"
    }

    private func formatBytes(_ bytes: UInt64) -> String {
        if bytes >= 1_073_741_824 {
            return String(format: "%.1f GB", Double(bytes) / 1_073_741_824)
        } else if bytes >= 1_048_576 {
            return String(format: "%.0f MB", Double(bytes) / 1_048_576)
        }
        return "\(bytes) B"
    }
}
