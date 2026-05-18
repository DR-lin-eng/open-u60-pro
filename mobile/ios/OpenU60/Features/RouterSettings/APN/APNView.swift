import SwiftUI

struct APNView: View {
    @Bindable var viewModel: APNViewModel
    @State private var showAutoDetail: APNProfile?

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

            if let activeName = viewModel.activeAPNName {
                Section("当前 APN") {
                    HStack {
                        Text(activeName)
                            .font(.headline)
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                    }
                }
            }

            Section("APN 模式") {
                Toggle("手动 APN", isOn: Binding(
                    get: { viewModel.config.isManual },
                    set: { manual in Task { await viewModel.setMode(manual: manual) } }
                ))
                .disabled(viewModel.isLoading)
            }

            if viewModel.config.isManual {
                Section {
                    Button {
                        viewModel.startAdd()
                    } label: {
                        Label("添加 APN", systemImage: "plus")
                    }
                } header: {
                    Text("手动配置")
                }

                if !viewModel.config.profiles.isEmpty {
                    Section {
                        ForEach(viewModel.config.profiles) { profile in
                            APNProfileRow(profile: profile)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    viewModel.startEdit(profile)
                                }
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        Task { await viewModel.deleteAPN(profile) }
                                    } label: {
                                        Label("删除", systemImage: "trash")
                                    }
                                    .disabled(profile.active)
                                }
                                .swipeActions(edge: .leading) {
                                    if !profile.active {
                                        Button {
                                            Task { await viewModel.activateAPN(profile) }
                                        } label: {
                                            Label("启用", systemImage: "checkmark.circle")
                                        }
                                        .tint(.green)
                                    }
                                }
                        }
                    }
                }
            } else {
                // Auto mode: show auto profiles read-only
                if !viewModel.config.autoProfiles.isEmpty {
                    Section("自动配置") {
                        ForEach(viewModel.config.autoProfiles) { profile in
                            APNProfileRow(profile: profile)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    showAutoDetail = profile
                                }
                        }
                    }
                }
            }
        }
        .navigationTitle("APN 设置")
        .refreshable { await viewModel.refresh() }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .task { await viewModel.refresh() }
        .sheet(isPresented: $viewModel.showFormSheet) {
            APNFormSheet(viewModel: viewModel)
        }
        .sheet(item: $showAutoDetail) { profile in
            APNAutoDetailSheet(profile: profile)
        }
    }
}

// MARK: - Profile Row

private struct APNProfileRow: View {
    let profile: APNProfile

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(profile.name.isEmpty ? "未命名" : profile.name)
                    .font(.headline)
                Spacer()
                if profile.active {
                    Text("当前使用")
                        .font(.caption)
                        .foregroundStyle(.green)
                }
            }
            Text(profile.apn)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("\(profile.pdpTypeLabel) / \(profile.authModeLabel)")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Add/Edit Form Sheet

struct APNFormSheet: View {
    @Bindable var viewModel: APNViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("配置") {
                    TextField("名称", text: $viewModel.formProfile.name)
                    TextField("APN", text: $viewModel.formProfile.apn)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }

                Section("连接") {
                    Picker("PDP 类型", selection: $viewModel.formProfile.pdpType) {
                        ForEach(APNProfile.pdpTypeOptions, id: \.value) { opt in
                            Text(opt.label).tag(opt.value)
                        }
                    }

                    Picker("认证模式", selection: $viewModel.formProfile.authMode) {
                        ForEach(APNProfile.authModeOptions, id: \.value) { opt in
                            Text(opt.label).tag(opt.value)
                        }
                    }
                }

                if viewModel.formProfile.authMode != 0 {
                    Section("凭据") {
                        TextField("用户名", text: $viewModel.formProfile.username)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        SecureField("密码", text: $viewModel.formProfile.password)
                    }
                }

                Section {
                    Toggle("设为默认", isOn: $viewModel.setAsDefault)
                }

                Section {
                    Button {
                        Task { await viewModel.saveAPN() }
                    } label: {
                        Text(viewModel.isEditing ? "保存" : "添加 APN")
                            .frame(maxWidth: .infinity)
                    }
                    .disabled(
                        viewModel.formProfile.name.isEmpty
                        || viewModel.formProfile.apn.isEmpty
                        || viewModel.isLoading
                    )
                }
            }
            .navigationTitle(viewModel.isEditing ? "编辑 APN" : "新建 APN")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Auto APN Detail (Read-Only)

struct APNAutoDetailSheet: View {
    let profile: APNProfile
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("配置") {
                    LabeledContent("名称", value: profile.name.isEmpty ? "—" : profile.name)
                    LabeledContent("APN", value: profile.apn.isEmpty ? "—" : profile.apn)
                }
                Section("连接") {
                    LabeledContent("PDP 类型", value: profile.pdpTypeLabel)
                    LabeledContent("认证模式", value: profile.authModeLabel)
                }
                if profile.authMode != 0 {
                    Section("凭据") {
                        LabeledContent("用户名", value: profile.username.isEmpty ? "—" : profile.username)
                    }
                }
                Section {
                    LabeledContent("状态", value: profile.active ? "当前使用" : "未启用")
                }
            }
            .navigationTitle("自动 APN")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}
