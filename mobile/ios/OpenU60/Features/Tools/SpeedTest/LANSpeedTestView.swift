import SwiftUI

struct LANSpeedTestView: View {
    @Bindable var viewModel: LANSpeedTestViewModel

    var body: some View {
        List {
            if let error = viewModel.error {
                Section {
                    Text(error)
                        .font(.subheadline)
                        .foregroundStyle(.red)
                }
            }

            Section("控制") {
                if viewModel.isRunning {
                    HStack {
                        Text(phaseLabel)
                        Spacer()
                        Text("\(Int(viewModel.progress * 100))%")
                            .foregroundStyle(.secondary)
                            .contentTransition(.numericText())
                            .animation(.default, value: viewModel.progress)
                    }
                    ProgressView(value: viewModel.progress)

                    if viewModel.phase == "download" || viewModel.phase == "upload" {
                        HStack {
                            Spacer()
                            if viewModel.phase == "download" {
                                Image(systemName: "arrow.down")
                                    .font(.title)
                                    .foregroundStyle(.blue)
                            } else {
                                Image(systemName: "arrow.up")
                                    .font(.title)
                                    .foregroundStyle(.orange)
                            }
                            Text(String(format: "%.1f", viewModel.liveSpeedMbps))
                                .font(.system(size: 48, weight: .bold, design: .rounded))
                                .contentTransition(.numericText())
                                .animation(.default, value: viewModel.liveSpeedMbps)
                            Text("Mbps")
                                .font(.title3)
                                .foregroundStyle(.secondary)
                            Spacer()
                        }
                        .padding(.vertical, 8)
                    }

                    Button("停止测试", role: .destructive) {
                        viewModel.stopTest()
                    }
                } else {
                    Button("开始局域网测速") {
                        viewModel.startTest()
                    }
                }
            }

            if viewModel.phase == "complete" {
                Section("结果") {
                    if let ping = viewModel.pingMs {
                        LabeledContent("延迟", value: String(format: "%.1f ms", ping))
                    }
                    if let download = viewModel.downloadMbps {
                        LabeledContent("下载", value: String(format: "%.1f Mbps", download))
                    }
                    if let upload = viewModel.uploadMbps {
                        LabeledContent("上传", value: String(format: "%.1f Mbps", upload))
                    }
                }
            }

            Section {
                Text("测量本设备与路由器之间的 Wi-Fi 链路速度，不消耗互联网流量。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("局域网测速")
    }

    private var phaseLabel: String {
        switch viewModel.phase {
        case "ping": return "测试延迟中..."
        case "download": return "下载中..."
        case "upload": return "上传中..."
        default: return viewModel.phase.capitalized
        }
    }
}
