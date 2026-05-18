import SwiftUI

struct LANSettingsView: View {
    @Bindable var viewModel: LANSettingsViewModel

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

            Section("LAN") {
                TextField("IP Address", text: $viewModel.editLanIP)
                    .keyboardType(.decimalPad)
                    .autocorrectionDisabled()
                TextField("子网掩码", text: $viewModel.editNetmask)
                    .keyboardType(.decimalPad)
                    .autocorrectionDisabled()
            }

            Section("DHCP 服务器") {
                Toggle("启用 DHCP", isOn: $viewModel.editDhcpEnabled)

                if viewModel.editDhcpEnabled {
                    TextField("起始地址", text: $viewModel.editDhcpStart)
                        .keyboardType(.decimalPad)
                        .autocorrectionDisabled()
                    TextField("结束地址", text: $viewModel.editDhcpEnd)
                        .keyboardType(.decimalPad)
                        .autocorrectionDisabled()
                    TextField("Lease Time (seconds)", text: $viewModel.editLeaseTime)
                        .keyboardType(.numberPad)
                }
            }

            Section {
                Button {
                    Task { await viewModel.apply() }
                } label: {
                    Text("应用")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.isLoading)
            }
        }
        .navigationTitle("局域网 / DHCP")
        .refreshable { await viewModel.refresh() }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .task { await viewModel.refresh() }
    }
}
