import SwiftUI

struct ClashView: View {
    @Bindable var viewModel: ClashViewModel

    var body: some View {
        List {
            if let msg = viewModel.message {
                Section {
                    Text(msg)
                        .font(.subheadline)
                        .foregroundStyle(viewModel.messageIsError ? .red : .green)
                        .textSelection(.enabled)
                }
            }

            Section("状态") {
                LabeledContent("状态", value: viewModel.status.running ? "在线" : "离线")
                LabeledContent("版本", value: viewModel.status.version.isEmpty ? "—" : viewModel.status.version)
                LabeledContent("面板", value: viewModel.status.ui.isEmpty ? "zashboard" : viewModel.status.ui)
                LabeledContent("控制端口", value: "\(viewModel.status.controllerPort)")
                LabeledContent("模式", value: viewModel.status.modeLabel)
                LabeledContent("连接数", value: "\(viewModel.status.connections)")
                LabeledContent("下载总量", value: DeviceParser.formatBytes(viewModel.status.downloadTotal))
                LabeledContent("上传总量", value: DeviceParser.formatBytes(viewModel.status.uploadTotal))
                LabeledContent("Mixed Port", value: "\(viewModel.status.mixedPort)")
                LabeledContent("TUN", value: viewModel.status.tunEnabled ? "已开启" : "未开启")
            }

            if viewModel.status.running {
                Section("模式") {
                    Picker("模式", selection: Binding(
                        get: { viewModel.status.mode.lowercased() },
                        set: { newValue in
                            Task { await viewModel.setMode(newValue) }
                        }
                    )) {
                        Text("规则").tag("rule")
                        Text("全局").tag("global")
                        Text("直连").tag("direct")
                    }
                    .pickerStyle(.segmented)
                    .disabled(viewModel.isLoading)
                }

                if !viewModel.selectors.isEmpty {
                    Section("策略组") {
                        ForEach(viewModel.selectors) { selector in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(selector.name)
                                    .font(.headline)
                                Menu {
                                    ForEach(selector.options, id: \.self) { option in
                                        Button(option) {
                                            Task { await viewModel.select(group: selector.name, option: option) }
                                        }
                                    }
                                } label: {
                                    HStack {
                                        Text(selector.current.isEmpty ? "未选择" : selector.current)
                                        Spacer()
                                        Text(selector.alive ? "可用" : "离线")
                                            .foregroundStyle(selector.alive ? .secondary : .orange)
                                    }
                                }
                                .disabled(viewModel.isLoading)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            } else {
                Section {
                    Text("当前无法直连 Clash 控制器，请确认 7788 端口可访问，且 secret 仍为默认 123456。")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Clash")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}
