import SwiftUI

struct DeviceControlView: View {
    @Bindable var viewModel: DeviceControlViewModel

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
                Toggle("充电上限", isOn: Binding(
                    get: { viewModel.chargeLimitEnabled },
                    set: { val in
                        viewModel.chargeLimitEnabled = val
                        Task { await viewModel.setChargeLimit(enabled: val, limit: viewModel.chargeLimit) }
                    }
                ))
                    .disabled(viewModel.isLoading)

                if viewModel.chargeLimitEnabled {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("在 \(viewModel.chargeLimit)% 停止")
                            .font(.subheadline.monospacedDigit())
                        Slider(
                            value: Binding(
                                get: { Double(viewModel.chargeLimit) },
                                set: { viewModel.chargeLimit = Int($0) }
                            ),
                            in: 50...100,
                            step: 5
                        ) {
                            Text("充电上限")
                        } onEditingChanged: { editing in
                            if !editing {
                                Task { await viewModel.setChargeLimit(enabled: true, limit: viewModel.chargeLimit) }
                            }
                        }
                        .disabled(viewModel.isLoading)
                    }

                    Stepper(
                        "恢复间隔：\(viewModel.hysteresis)%",
                        value: Binding(
                            get: { viewModel.hysteresis },
                            set: { newVal in
                                viewModel.hysteresis = newVal
                                Task { await viewModel.setChargeLimit(enabled: true, limit: viewModel.chargeLimit, hysteresis: newVal) }
                            }
                        ),
                        in: 1...20
                    )
                    .disabled(viewModel.isLoading)
                }
            } footer: {
                if viewModel.chargeLimitEnabled {
                    Text("充电会在 \(viewModel.chargeLimit)% 时停止，并在 \(viewModel.chargeLimit - viewModel.hysteresis)% 时恢复。\n\n恢复间隔可以避免充电器频繁启停。较小的间隔（例如 2%）能让电量更贴近目标值，但切换会更频繁；较大的间隔（例如 10%）可减少充放电循环次数，但电量波动会更明显。\n\n默认值：5%，适合大多数用户。")
                } else {
                    Text("电量达到设定值时停止充电，可延长电池寿命。")
                }
            }

            Section {
                Toggle("省电模式", isOn: Binding(
                    get: { viewModel.powerSaveEnabled },
                    set: { val in
                        viewModel.powerSaveEnabled = val
                        Task { await viewModel.setPowerSave(enabled: val) }
                    }
                ))
                    .disabled(viewModel.isLoading)
            } footer: {
                Text("限制数据通信速率以降低功耗并延长续航。")
            }

            Section {
                Toggle("快速启动", isOn: Binding(
                    get: { viewModel.fastBootEnabled },
                    set: { val in
                        viewModel.fastBootEnabled = val
                        Task { await viewModel.setFastBoot(enabled: val) }
                    }
                ))
                    .disabled(viewModel.isLoading)
            } footer: {
                Text("启用后，关机将进入内存挂起状态以实现近乎瞬时开机；关闭后则执行完整关机（关机时更省电）。")
            }

            Section {
                Button("重启路由器") {
                    viewModel.showRebootConfirm = true
                }
                .disabled(viewModel.isLoading)
            } footer: {
                Text("路由器将重新启动，大约需要 60 秒。")
            }

            Section {
                Button("恢复出厂设置", role: .destructive) {
                    viewModel.showFactoryResetConfirm = true
                }
                .disabled(viewModel.isLoading)
            } footer: {
                Text("这将清除所有设置并恢复出厂默认值，且无法撤销。")
            }
        }
        .task { await viewModel.refresh() }
        .navigationTitle("设备控制")
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .sheet(isPresented: $viewModel.showRebootConfirm) {
            PasswordConfirmView(
                title: "重启路由器",
                message: "输入路由器密码以确认重启。",
                confirmLabel: "重启"
            ) {
                await viewModel.reboot()
            }
        }
        .sheet(isPresented: $viewModel.showFactoryResetConfirm) {
            PasswordConfirmView(
                title: "恢复出厂设置",
                message: "这将清除所有设置。请输入路由器密码以确认。",
                confirmLabel: "恢复出厂设置"
            ) {
                await viewModel.factoryReset()
            }
        }
    }
}
