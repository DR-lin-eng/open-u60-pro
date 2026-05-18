import SwiftUI

struct ProcessListSheet: View {
    let client: AgentClient

    @State private var processes: [ProcessInfo] = []
    @State private var bloatCount = 0
    @State private var bloatCpuPct = 0.0
    @State private var bloatRssKb = 0
    @State private var isLoading = false
    @State private var error: String?
    @State private var banner: String?
    @State private var showKillAllConfirm = false
    @State private var refreshTimer: Timer?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && processes.isEmpty {
                    ProgressView("正在加载进程...")
                } else {
                    List {
                        if bloatCount > 0 {
                            Section {
                                HStack {
                                    Label("臃肿守护进程", systemImage: "exclamationmark.triangle")
                                        .foregroundStyle(.orange)
                                    Spacer()
                                    VStack(alignment: .trailing) {
                                        Text("\(bloatCount) 个进程")
                                            .font(.caption)
                                        Text(String(format: "%.1f%% CPU，%@ RSS", bloatCpuPct, formatKB(bloatRssKb)))
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }

                        if let error {
                            Section {
                                Text(error)
                                    .foregroundStyle(.red)
                                    .font(.caption)
                            }
                        }

                        if let banner {
                            Section {
                                Text(banner)
                                    .foregroundStyle(.green)
                                    .font(.caption)
                            }
                        }

                        Section(header: Text("主要进程")) {
                            ForEach(processes) { proc in
                                processRow(proc)
                                    .swipeActions(edge: .trailing) {
                                        if proc.isBloat {
                                            Button(role: .destructive) {
                                                Task { await killSingle(proc.pid) }
                                            } label: {
                                                Label("结束", systemImage: "xmark.circle")
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            }
            .navigationTitle("进程")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("完成") { dismiss() }
                }
                if bloatCount > 0 {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("结束全部臃肿进程", role: .destructive) {
                            showKillAllConfirm = true
                        }
                        .foregroundStyle(.red)
                    }
                }
            }
            .confirmationDialog("结束全部臃肿守护进程？", isPresented: $showKillAllConfirm, titleVisibility: .visible) {
                Button("结束全部臃肿进程", role: .destructive) {
                    Task { await killAll() }
                }
            } message: {
                Text("这将对 \(bloatCount) 个臃肿守护进程发送 SIGKILL。设备重启后它们会重新出现。")
            }
            .task {
                await refresh()
                startTimer()
            }
            .onDisappear {
                refreshTimer?.invalidate()
            }
        }
    }

    @ViewBuilder
    private func processRow(_ proc: ProcessInfo) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(proc.name)
                    .font(.body)
                    .foregroundStyle(proc.isBloat ? .orange : .primary)
                Text("PID \(proc.pid) \u{00B7} \(proc.state)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(String(format: "%.1f%%", proc.cpuPct))
                    .font(.body.monospacedDigit())
                    .foregroundStyle(proc.cpuPct > 10 ? .red : (proc.cpuPct > 2 ? .orange : .secondary))
                Text(formatKB(proc.rssKb))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func refresh() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let result: ProcessListResponse = try await client.get("/api/system/top")
            processes = result.processes
            bloatCount = result.bloatCount
            bloatCpuPct = result.bloatCpuPct
            bloatRssKb = result.bloatRssKb
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func killSingle(_ pid: Int) async {
        do {
            let body = ["pids": [pid]] as [String: Any]
            let data = try await client.postJSON("/api/system/kill-bloat", body: body)
            let freed = data["freed_rss_kb"] as? Int ?? 0
            banner = "已结束 PID \(pid)，释放 \(formatKB(freed))"
            await refresh()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func killAll() async {
        do {
            let data = try await client.postJSON("/api/system/kill-bloat", body: ["all": true])
            let freed = data["freed_rss_kb"] as? Int ?? 0
            let killedArr = data["killed"] as? [[String: Any]] ?? []
            banner = "已结束 \(killedArr.count) 个守护进程，释放 \(formatKB(freed))"
            await refresh()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func startTimer() {
        refreshTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { _ in
            Task { @MainActor in
                await refresh()
            }
        }
    }

    private func formatKB(_ kb: Int) -> String {
        if kb >= 1024 {
            return String(format: "%.1f MB", Double(kb) / 1024.0)
        }
        return "\(kb) KB"
    }
}
