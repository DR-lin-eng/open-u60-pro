import SwiftUI

struct SpeedTestView: View {
    @Bindable var viewModel: SpeedTestViewModel

    var body: some View {
        List {
            if let msg = viewModel.message {
                Section {
                    Text(msg)
                        .font(.subheadline)
                        .foregroundStyle(viewModel.messageIsError ? .red : .green)
                }
            }

            Section("服务器") {
                Picker("服务器", selection: $viewModel.selectedServerId) {
                    Text("选择服务器").tag(nil as Int?)
                    ForEach(viewModel.servers) { server in
                        Text("\(server.sponsor) - \(server.name), \(server.country)")
                            .tag(server.id as Int?)
                    }
                }
                .disabled(viewModel.isRunning)
            }

            Section("控制") {
                if viewModel.isRunning {
                    HStack {
                        Text(phaseLabel)
                        Spacer()
                        Text("\(viewModel.progress.progress)%")
                            .foregroundStyle(.secondary)
                            .contentTransition(.numericText())
                            .animation(.default, value: viewModel.progress.progress)
                    }
                    ProgressView(value: Double(viewModel.progress.progress), total: 100)

                    HStack {
                        Spacer()
                        if viewModel.progress.phase == "download" {
                            Image(systemName: "arrow.down")
                                .font(.title)
                                .foregroundStyle(.blue)
                        } else if viewModel.progress.phase == "upload" {
                            Image(systemName: "arrow.up")
                                .font(.title)
                                .foregroundStyle(.orange)
                        }
                        Text(String(format: "%.1f", viewModel.progress.liveSpeedMbps))
                            .font(.system(size: 48, weight: .bold, design: .rounded))
                            .contentTransition(.numericText())
                            .animation(.default, value: viewModel.progress.liveSpeedMbps)
                        Text("Mbps")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                        Spacer()
                    }
                    .padding(.vertical, 8)

                    Button("停止测试", role: .destructive) {
                        Task { await viewModel.stopTest() }
                    }
                } else {
                    Button("开始测速") {
                        Task { await viewModel.startTest() }
                    }
                    .disabled(viewModel.isLoading || viewModel.selectedServerId == nil)
                }
            }

            if viewModel.progress.phase == "complete" {
                Section("结果") {
                    if let ping = viewModel.progress.pingMs {
                        LabeledContent("延迟", value: String(format: "%.1f ms", ping))
                    }
                    if let jitter = viewModel.progress.jitterMs {
                        LabeledContent("抖动", value: String(format: "%.1f ms", jitter))
                    }
                    if let download = viewModel.progress.downloadMbps {
                        LabeledContent("下载", value: String(format: "%.2f Mbps", download))
                    }
                    if let upload = viewModel.progress.uploadMbps {
                        LabeledContent("上传", value: String(format: "%.2f Mbps", upload))
                    }
                }

                Section("传输") {
                    LabeledContent("已下载", value: formatBytes(viewModel.progress.downloadBytes))
                    LabeledContent("已上传", value: formatBytes(viewModel.progress.uploadBytes))
                    if !viewModel.progress.server.isEmpty {
                        LabeledContent("服务器", value: viewModel.progress.server)
                    }
                }
            }
        }
        .navigationTitle("速度测试")
        .task { await viewModel.loadServers() }
        .overlay {
            if viewModel.isLoading && !viewModel.isRunning {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private var phaseLabel: String {
        switch viewModel.progress.phase {
        case "latency": return "测试延迟中..."
        case "download": return "下载中..."
        case "upload": return "上传中..."
        default: return viewModel.progress.phase.capitalized
        }
    }

    private func formatBytes(_ bytes: Int) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        let kb = Double(bytes) / 1024
        if kb < 1024 { return String(format: "%.1f KB", kb) }
        let mb = kb / 1024
        if mb < 1024 { return String(format: "%.1f MB", mb) }
        let gb = mb / 1024
        return String(format: "%.2f GB", gb)
    }
}
