import SwiftUI

struct SettingsView: View {
    @State private var viewModel: SettingsViewModel

    init(client: AgentClient) {
        _viewModel = State(initialValue: SettingsViewModel(client: client))
    }

    var body: some View {
        @Bindable var vm = viewModel
        NavigationStack {
            Form {
                Section("网关") {
                    HStack {
                        TextField("网关 IP", text: $vm.gatewayIP)
                            .keyboardType(.decimalPad)
                            .autocorrectionDisabled()
                        Button("检测") {
                            Task { await viewModel.autoDetectGateway() }
                        }
                        .buttonStyle(.bordered)
                        .font(.caption)
                        .disabled(viewModel.isDetectingGateway)
                        if viewModel.isDetectingGateway {
                            ProgressView()
                        }
                    }
                }

                Section("认证") {
                    if viewModel.hasStoredPassword {
                        HStack {
                            Text("密码已存入钥匙串")
                                .foregroundStyle(.secondary)
                            Spacer()
                            Button("清除", role: .destructive) {
                                viewModel.clearPassword()
                            }
                            .font(.caption)
                        }
                    }
                    SecureField("新密码", text: $vm.passwordInput)
                    Button("保存到钥匙串") {
                        viewModel.savePassword()
                    }
                    .disabled(viewModel.passwordInput.isEmpty)
                }

                Section("轮询") {
                    VStack(alignment: .leading) {
                        Text("刷新间隔：\(viewModel.pollInterval, specifier: "%.1f") 秒")
                        Slider(value: $vm.pollInterval, in: 1...10, step: 0.5)
                    }
                }

                Section("外观") {
                    Picker("主题", selection: $vm.darkModeOverride) {
                        Text("跟随系统").tag(0)
                        Text("浅色").tag(1)
                        Text("深色").tag(2)
                    }
                    .pickerStyle(.segmented)
                }

                Section("关于") {
                    LabeledContent("应用", value: "OpenU60")
                    LabeledContent("设备", value: "ZTE U60 Pro (MU5250)")
                    LabeledContent("API", value: "zte-agent REST")
                }

                Section("法律声明") {
                    Text("本应用与中兴通讯股份有限公司无关联，也未获得其认可或赞助。ZTE 和 U60 Pro 是中兴通讯股份有限公司的商标。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Link("隐私政策", destination: URL(string: "https://open-u60-pro.vercel.app/privacy")!)
                }
            }
            .navigationTitle("设置")
            .overlay {
                if viewModel.showSavedConfirmation {
                    savedToast
                }
            }
        }
    }

    private var savedToast: some View {
        Text("密码已保存")
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 10))
            .transition(.move(edge: .top).combined(with: .opacity))
            .task {
                try? await Task.sleep(for: .seconds(1.5))
                withAnimation { viewModel.showSavedConfirmation = false }
            }
    }
}
