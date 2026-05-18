import SwiftUI

struct SMSForwardLogView: View {
    @Bindable var viewModel: SMSForwardViewModel

    var body: some View {
        List {
            ForEach(Array(viewModel.log.enumerated()), id: \.element.id) { index, entry in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(entry.sender)
                            .font(.headline)
                        Spacer()
                        Image(systemName: entry.success ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .foregroundStyle(entry.success ? .green : .red)
                    }
                    Text("\(entry.ruleName) → \(entry.destinationType)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(entry.contentPreview)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    if let error = entry.error {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                    Text(formatTimestamp(entry.timestamp))
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
                .padding(.vertical, 2)
                .swipeActions(edge: .trailing) {
                    if !entry.success {
                        Button {
                            Task { await viewModel.retryForward(index: index) }
                        } label: {
                            Label("重试", systemImage: "arrow.clockwise")
                        }
                        .tint(.orange)
                    }
                }
            }
        }
        .navigationTitle("转发日志")
        .toolbar {
            Button("清除") {
                Task { await viewModel.clearLog() }
            }
        }
        .overlay {
            if viewModel.log.isEmpty && !viewModel.isLoading {
                ContentUnavailableView("暂无日志", systemImage: "doc.text",
                                       description: Text("已转发的短信会显示在这里"))
            }
        }
        .task { await viewModel.fetchLog() }
    }

    private func formatTimestamp(_ ts: Int) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(ts))
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .medium
        return formatter.string(from: date)
    }
}
