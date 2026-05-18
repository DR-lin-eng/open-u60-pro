import SwiftUI

struct STKMenuView: View {
    @Bindable var viewModel: STKViewModel

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

            ussdSection

            if viewModel.showUssdResponse {
                ussdResponseSection
            }

            stkSection
        }
        .navigationTitle("SIM 服务")
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .task { await viewModel.loadSTKMenu() }
    }

    // MARK: - USSD

    private var ussdSection: some View {
        Section {
            HStack {
                TextField("USSD 代码（例如 *100#）", text: $viewModel.ussdCode)
                    .keyboardType(.phonePad)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)

                Button {
                    Task { await viewModel.sendUSSD() }
                } label: {
                    Image(systemName: "paperplane.fill")
                }
                .disabled(viewModel.ussdCode.isEmpty || viewModel.isLoading)
            }
        } header: {
            Text("USSD")
        } footer: {
            Text("发送运营商服务代码以查询余额、流量套餐等。")
        }
    }

    private var ussdResponseSection: some View {
        Section("响应") {
            Text(viewModel.ussdResponse.response.isEmpty
                 ? viewModel.ussdResponse.rawResponse
                 : viewModel.ussdResponse.response)
                .font(.body)
                .textSelection(.enabled)

            if viewModel.ussdResponse.sessionActive {
                HStack {
                    TextField("回复", text: $viewModel.ussdReply)
                        .keyboardType(.phonePad)

                    Button {
                        Task { await viewModel.respondUSSD() }
                    } label: {
                        Image(systemName: "arrow.up.circle.fill")
                    }
                    .disabled(viewModel.ussdReply.isEmpty || viewModel.isLoading)
                }

                Button(role: .destructive) {
                    Task { await viewModel.cancelUSSD() }
                } label: {
                    Label("结束会话", systemImage: "xmark.circle")
                }
            }
        }
    }

    // MARK: - STK

    private var stkSection: some View {
        Group {
            if viewModel.stkNotSupported {
                Section("SIM 工具包") {
                    Label("此 SIM 卡不可用", systemImage: "simcard")
                        .foregroundStyle(.secondary)
                }
            } else if viewModel.hasSTKMenu {
                Section {
                    ForEach(viewModel.stkMenu.items) { item in
                        Button {
                            Task { await viewModel.selectSTKItem(item) }
                        } label: {
                            Label(item.label, systemImage: "list.bullet")
                                .foregroundStyle(.primary)
                        }
                        .disabled(viewModel.isLoading)
                    }

                    if !viewModel.menuStack.isEmpty {
                        Button {
                            viewModel.goBackSTK()
                        } label: {
                            Label("返回", systemImage: "chevron.left")
                        }
                    }
                } header: {
                    Text(viewModel.stkMenu.title.isEmpty ? "SIM 工具包" : viewModel.stkMenu.title)
                } footer: {
                    Text("由 SIM 卡提供的运营商服务")
                }
            } else if viewModel.message == nil && !viewModel.isLoading {
                Section("SIM 工具包") {
                    Button {
                        Task { await viewModel.loadSTKMenu() }
                    } label: {
                        Label("重新加载菜单", systemImage: "arrow.clockwise")
                    }
                }
            }
        }
    }
}
