import SwiftUI

struct FirewallSettingsView: View {
    @Bindable var viewModel: FirewallSettingsViewModel

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

            Section("防火墙") {
                LabeledContent("状态") {
                    Text(viewModel.config.enabled ? "已启用" : "已禁用")
                        .foregroundStyle(viewModel.config.enabled ? .green : .secondary)
                }

                Button(viewModel.config.enabled ? "禁用防火墙" : "启用防火墙") {
                    Task { await viewModel.toggleFirewall(enabled: !viewModel.config.enabled) }
                }
                .disabled(viewModel.isLoading)

                if viewModel.config.enabled {
                    Picker("级别", selection: Binding(
                        get: { viewModel.config.level },
                        set: { level in Task { await viewModel.setLevel(level) } }
                    )) {
                        Text("低").tag("low")
                        Text("中").tag("medium")
                        Text("高").tag("high")
                    }
                    .disabled(viewModel.isLoading)
                }

                LabeledContent("WAN Ping", value: viewModel.config.wanPingFilter ? "已阻止" : "允许")
            }

            Section("NAT / UPnP") {
                Toggle("NAT", isOn: Binding(
                    get: { viewModel.config.nat },
                    set: { enabled in Task { await viewModel.toggleNAT(enabled: enabled) } }
                ))
                .disabled(viewModel.isLoading)

                Toggle("UPnP", isOn: Binding(
                    get: { viewModel.upnpEnabled },
                    set: { enabled in Task { await viewModel.toggleUPnP(enabled: enabled) } }
                ))
                .disabled(viewModel.isLoading)
            }

            Section("DMZ") {
                Toggle("启用 DMZ", isOn: $viewModel.editDmzEnabled)
                if viewModel.editDmzEnabled {
                    TextField("DMZ 主机 IP", text: $viewModel.editDmzIP)
                        .keyboardType(.decimalPad)
                        .autocorrectionDisabled()
                }
                Button {
                    Task { await viewModel.applyDMZ() }
                } label: {
                    Text("应用 DMZ")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.isLoading)
            }

            Section("端口转发") {
                Toggle("Enable", isOn: Binding(
                    get: { viewModel.config.portForwardEnabled },
                    set: { enabled in Task { await viewModel.togglePortForward(enabled: enabled) } }
                ))
                .disabled(viewModel.isLoading)

                if viewModel.config.portForwardEnabled {
                    Button {
                        viewModel.showAddPortForward = true
                    } label: {
                        Label("添加规则", systemImage: "plus")
                    }

                    ForEach(viewModel.portForwardRules) { rule in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(rule.name.isEmpty ? "规则" : rule.name)
                                    .font(.headline)
                                Spacer()
                                Text(rule.enabled ? "当前使用" : "未启用")
                                    .font(.caption)
                                    .foregroundStyle(rule.enabled ? .green : .secondary)
                            }
                            Text("\(rule.protocol_.uppercased()) WAN:\(rule.wanPort) \u{2192} \(rule.lanIP):\(rule.lanPort)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 2)
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                Task { await viewModel.deletePortForward(rule) }
                            } label: {
                                Label("删除", systemImage: "trash")
                            }
                        }
                    }
                }
            }

            if !viewModel.filterRules.isEmpty {
                Section("MAC/IP/端口过滤") {
                    ForEach(viewModel.filterRules) { rule in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(rule.protocol_.uppercased())
                                    .font(.headline)
                                Spacer()
                                Text(rule.enabled ? "当前使用" : "未启用")
                                    .font(.caption)
                                    .foregroundStyle(rule.enabled ? .green : .secondary)
                            }
                            if !rule.srcMac.isEmpty {
                                Text("MAC: \(rule.srcMac)")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            if !rule.srcIP.isEmpty || !rule.destIP.isEmpty {
                                Text("\(rule.srcIP):\(rule.srcPort) \u{2192} \(rule.destIP):\(rule.destPort)")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .navigationTitle("防火墙")
        .refreshable { await viewModel.refresh() }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .task { await viewModel.refresh() }
        .sheet(isPresented: $viewModel.showAddPortForward) {
            PortForwardFormView(viewModel: viewModel)
        }
    }
}
