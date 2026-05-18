import SwiftUI

struct USBModeSheetView: View {
    @Bindable var viewModel: USBConnectionViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                Image(systemName: "cable.connector")
                    .font(.system(size: 64))
                    .foregroundStyle(.blue)

                Text("USB-C 已连接")
                    .font(.title2.bold())

                Text("已有 USB-C 线缆连接到你的 U60 Pro。")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)

                if viewModel.usbStatus.powerbankActive {
                    Label("快速充电已启用", systemImage: "bolt.fill")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 32)
                } else {
                    if let msg = viewModel.message {
                        Text(msg)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(viewModel.messageIsError ? .red : .green)
                            .textSelection(.enabled)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }

                    Button {
                        Task {
                            await viewModel.enablePowerbank()
                            dismiss()
                        }
                    } label: {
                        Group {
                            if viewModel.isLoading {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Label("快速充电", systemImage: "bolt.fill")
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
                    .padding(.horizontal, 40)
                    .disabled(viewModel.isLoading)

                    Text("使用 U60 Pro 电池为手机充电")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Button("稍后") {
                    dismiss()
                }
                .padding(.top, 8)

                Spacer()
                Spacer()
            }
            .navigationTitle("USB 模式")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
