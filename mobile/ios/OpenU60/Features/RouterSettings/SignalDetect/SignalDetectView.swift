import SwiftUI

struct SignalDetectView: View {
    var viewModel: SignalDetectViewModel

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

            Section("控制") {
                if viewModel.status.running {
                    HStack {
                        Text("进度")
                        Spacer()
                        Text("\(viewModel.status.progress)%")
                            .foregroundStyle(.secondary)
                    }
                    ProgressView(value: Double(viewModel.status.progress), total: 100)

                    Button("停止检测", role: .destructive) {
                        Task { await viewModel.stopDetection() }
                    }
                } else {
                    Button("开始信号检测") {
                        Task { await viewModel.startDetection() }
                    }
                    .disabled(viewModel.isLoading)
                }
            }

            if !viewModel.status.results.isEmpty {

                Section("结果") {
                    ForEach(viewModel.status.results) { result in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(result.type)
                                    .font(.headline)
                                Text("频段 \(result.band)")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            HStack(spacing: 12) {
                                Label(result.rsrp, systemImage: "antenna.radiowaves.left.and.right")
                                Label(result.sinr, systemImage: "waveform")
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            Text("PCI: \(result.pci)  EARFCN: \(result.earfcn)")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .navigationTitle("信号检测")
        .overlay {
            if viewModel.isLoading {
                ProgressView()
                    .padding()
                    .background(Color(.systemBackground).opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}
