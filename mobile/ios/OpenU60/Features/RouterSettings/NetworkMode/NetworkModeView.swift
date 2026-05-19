import SwiftUI

struct NetworkModeView: View {
    @Bindable var viewModel: NetworkModeViewModel
    @State private var pendingConfirmation: PendingNetworkModeConfirmation?

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

            Section("当前") {
                LabeledContent("网络模式", value: displayLabel(for: viewModel.config.netSelect, in: NetworkModeConfig.netSelectOptions))
            }

            Section("网络模式") {
                Picker("模式", selection: $viewModel.selectedNetSelect) {
                    ForEach(NetworkModeConfig.netSelectOptions, id: \.value) { option in
                        Text(option.label).tag(option.value)
                    }
                }
                .pickerStyle(.menu)
            }

            Section {
                Button {
                    let modeLabel = displayLabel(for: viewModel.selectedNetSelect, in: NetworkModeConfig.netSelectOptions)
                    pendingConfirmation = PendingNetworkModeConfirmation(
                        title: "切换网络模式？",
                        message: "切换到 \(modeLabel) 后，设备可能会短暂掉线并重新注册网络。",
                        confirmLabel: "继续切换",
                        action: {
                            await viewModel.applyMode()
                        }
                    )
                } label: {
                    Text("应用")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.isLoading || viewModel.selectedNetSelect == viewModel.config.netSelect)
            }
        }
        .navigationTitle("网络模式")
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
        .task { await viewModel.refresh() }
    }

    private func displayLabel(for value: String, in options: [(label: String, value: String)]) -> String {
        options.first(where: { $0.value == value })?.label ?? (value.isEmpty ? "—" : value)
    }
}

private struct PendingNetworkModeConfirmation: Identifiable {
    let id = UUID()
    let title: String
    let message: String
    let confirmLabel: String
    let action: @MainActor () async -> Void
}
