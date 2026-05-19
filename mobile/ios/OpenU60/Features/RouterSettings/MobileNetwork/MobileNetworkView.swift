import SwiftUI

struct MobileNetworkView: View {
    @Bindable var viewModel: MobileNetworkViewModel
    @State private var pendingConfirmation: PendingMobileNetworkConfirmation?

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

            Section {
                Toggle("飞行模式", isOn: Binding(
                    get: { viewModel.airplaneModeEnabled },
                    set: { val in
                        pendingConfirmation = PendingMobileNetworkConfirmation(
                            title: val ? "开启飞行模式？" : "关闭飞行模式？",
                            message: val
                                ? "开启后会立即断开蜂窝网络与移动数据，当前联网状态可能中断。"
                                : "关闭后设备会尝试恢复蜂窝联网。根据当前固件限制，可能仍需重启才能完全恢复。",
                            confirmLabel: val ? "继续开启" : "继续关闭",
                            action: {
                                viewModel.airplaneModeEnabled = val
                                await viewModel.setAirplaneMode(enabled: val)
                            }
                        )
                    }
                ))
                .disabled(viewModel.isLoading)

                Toggle("移动数据", isOn: Binding(
                    get: { viewModel.selectedDataEnabled },
                    set: { val in
                        pendingConfirmation = PendingMobileNetworkConfirmation(
                            title: val ? "开启移动数据？" : "关闭移动数据？",
                            message: val
                                ? "开启后设备会尝试重新建立蜂窝连接，网络状态可能在数秒内波动。"
                                : "关闭后会立即断开当前蜂窝数据连接，依赖移动网络的访问会中断。",
                            confirmLabel: val ? "继续开启" : "继续关闭",
                            action: {
                                viewModel.selectedDataEnabled = val
                                await viewModel.setMobileData(enabled: val)
                            }
                        )
                    }
                ))
                .disabled(viewModel.isLoading || viewModel.airplaneModeEnabled)
            } header: {
                Text("连接设置")
            } footer: {
                Text(mobileDataFooter)
            }

            Section("连接模式") {
                Picker("模式", selection: $viewModel.selectedConnectMode) {
                    Text("自动").tag(1)
                    Text("手动").tag(0)
                }
                .pickerStyle(.segmented)
                .disabled(viewModel.isLoading || viewModel.airplaneModeEnabled)
            }

            Section {
                Toggle("数据漫游", isOn: $viewModel.selectedRoaming)
                    .disabled(viewModel.isLoading || viewModel.airplaneModeEnabled)
            } footer: {
                Text("启用漫游后，运营商可能会收取额外费用。")
            }

            Section("网络选择") {
                Picker("模式", selection: $viewModel.selectedNetSelectMode) {
                    Text("自动").tag("auto_select")
                    Text("手动").tag("manual_select")
                }
                .pickerStyle(.segmented)
                .disabled(viewModel.isLoading || viewModel.airplaneModeEnabled)

                if viewModel.selectedNetSelectMode == "manual_select" {
                    Button {
                        Task { await viewModel.scanNetworks() }
                    } label: {
                        HStack {
                            Text("扫描网络")
                            Spacer()
                            if viewModel.isScanning {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(viewModel.isScanning)

                    ForEach(viewModel.config.operators) { op in
                        Button {
                            Task { await viewModel.registerNetwork(mccMnc: op.mccMnc, rat: op.rat) }
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(op.name)
                                        .foregroundStyle(.primary)
                                    Text("\(op.mccMnc) · \(op.rat)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if op.status == "current" {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.green)
                                } else if op.status == "forbidden" {
                                    Image(systemName: "xmark.circle")
                                        .foregroundStyle(.red)
                                }
                            }
                        }
                        .disabled(viewModel.isLoading || op.status == "forbidden")
                    }
                }
            }

            Section {
                Button {
                    if viewModel.requiresSettingsConfirmation {
                        pendingConfirmation = PendingMobileNetworkConfirmation(
                            title: "应用移动网络设置？",
                            message: viewModel.settingsConfirmationMessages.joined(separator: "\n") + "\n\n应用后设备可能短暂掉线或重新注册网络。",
                            confirmLabel: "继续应用",
                            action: {
                                await viewModel.applySettings()
                            }
                        )
                    } else {
                        Task { await viewModel.applySettings() }
                    }
                } label: {
                    Text("应用")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.isLoading || !viewModel.hasChanges || viewModel.airplaneModeEnabled)
            }
        }
        .navigationTitle("移动网络")
        .refreshable { await viewModel.refresh() }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .confirmationDialog(
            pendingConfirmation?.title ?? "",
            isPresented: Binding(
                get: { pendingConfirmation != nil },
                set: { newValue in
                    if !newValue { pendingConfirmation = nil }
                }
            ),
            titleVisibility: .visible,
            presenting: pendingConfirmation
        ) { confirmation in
            Button(confirmation.confirmLabel) {
                let action = confirmation.action
                pendingConfirmation = nil
                Task { await action() }
            }
            Button("取消", role: .cancel) {
                pendingConfirmation = nil
            }
        } message: { confirmation in
            Text(confirmation.message)
        }
        .alert("需要重启", isPresented: $viewModel.showRebootAfterAirplaneOff) {
            Button("立即重启") {
                Task { await viewModel.reboot() }
            }
            Button("取消", role: .cancel) {
                viewModel.airplaneModeEnabled = true
            }
        } message: {
            Text("由于固件限制，蜂窝无线在不重启的情况下无法恢复。路由器将重启，约需 60 秒。")
        }
        .task { await viewModel.refresh() }
    }

    private var mobileDataFooter: String {
        if !viewModel.config.isDataEnabled && viewModel.config.isConnected {
            return "移动数据开关已关闭，但连接仍处于活动状态。"
        } else if !viewModel.config.isDataEnabled {
            return "移动数据已禁用。"
        } else if viewModel.config.isConnected {
            let status = viewModel.config.connectStatus
                .replacingOccurrences(of: "_", with: " ")
                .capitalized
            return "已连接 — \(status)"
        } else if !viewModel.config.connectStatus.isEmpty {
            return "未连接"
        } else {
            return "关闭移动数据会断开蜂窝连接。"
        }
    }
}

private struct PendingMobileNetworkConfirmation: Identifiable {
    let id = UUID()
    let title: String
    let message: String
    let confirmLabel: String
    let action: @MainActor () async -> Void
}
