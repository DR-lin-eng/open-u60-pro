import SwiftUI

struct BatteryCardView: View {
    let battery: BatteryStatus

    var body: some View {
        CardView {
            VStack(spacing: 8) {
                Image(systemName: batteryIcon(battery.capacity))
                    .font(.title2)
                    .foregroundStyle(Color.batteryColor(battery.capacity))
                AnimatedNumber(value: battery.capacity,
                               font: .title3.weight(.bold), textColor: .primary, suffix: "%")
                batteryStatusLine
                if battery.temperature > 0 {
                    AnimatedNumber(value: battery.temperature, decimalPlaces: 0,
                                   font: .caption, textColor: .secondary, suffix: "\u{00B0}C")
                }
                Text("电池")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private var batteryStatusLine: some View {
        HStack(spacing: 4) {
            batteryStatusText
            if let ma = battery.currentMA {
                Text("\u{00B7}").font(.caption).foregroundStyle(.secondary)
                if let mv = battery.voltageMV {
                    let watts = Double(mv) * Double(abs(ma)) / 1_000_000.0
                    AnimatedNumber(value: watts, decimalPlaces: 1,
                                   font: .caption.monospacedDigit(),
                                   textColor: batteryStatusColor,
                                   suffix: "W")
                } else {
                    AnimatedNumber(value: ma,
                                   font: .caption.monospacedDigit(),
                                   textColor: batteryStatusColor,
                                   prefix: ma >= 0 ? "+" : nil,
                                   suffix: "mA")
                }
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.8)
    }

    @ViewBuilder
    private var batteryStatusText: some View {
        switch battery.charging {
        case "stopped":
            Text("已停止充电")
                .font(.caption)
                .foregroundStyle(.orange)
        case "charging":
            if battery.capacity >= 100 {
                Text("已充满")
                    .font(.caption)
                    .foregroundStyle(.green)
            } else if battery.currentMA != nil {
                Text("充电中")
                    .font(.caption)
                    .foregroundStyle(.green)
            } else if battery.timeToFull > 0 {
                Text("充电中 \u{00B7} \(formatETA(battery.timeToFull))")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.green)
            } else {
                Text("充电中")
                    .font(.caption)
                    .foregroundStyle(.green)
            }
        default:
            if battery.timeToEmpty > 0 {
                if battery.currentMA != nil {
                    Text(formatETA(battery.timeToEmpty))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(Color.batteryColor(battery.capacity))
                } else {
                    Text("剩余 \(formatETA(battery.timeToEmpty))")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(Color.batteryColor(battery.capacity))
                }
            } else {
                Text("放电中")
                    .font(.caption)
                    .foregroundStyle(Color.batteryColor(battery.capacity))
            }
        }
    }

    private var batteryStatusColor: Color {
        switch battery.charging {
        case "stopped": return .orange
        case "charging": return .green
        default: return Color.batteryColor(battery.capacity)
        }
    }

    private func formatETA(_ minutes: Int) -> String {
        if minutes >= 1440 {
            let d = minutes / 1440, h = (minutes % 1440) / 60, m = minutes % 60
            return "\(d)天 \(h)小时 \(m)分"
        }
        return minutes >= 60 ? "\(minutes / 60)小时 \(minutes % 60)分" : "\(minutes)分"
    }

    private func batteryIcon(_ percent: Int) -> String {
        if percent >= 75 { return "battery.100" }
        if percent >= 50 { return "battery.75" }
        if percent >= 25 { return "battery.50" }
        return "battery.25"
    }
}

struct BatteryDetailSheet: View {
    let battery: BatteryStatus

    var body: some View {
        NavigationStack {
            List {
                row("电量", icon: batteryIcon(battery.capacity), value: "\(battery.capacity)%")
                row("状态", icon: "bolt.fill", value: statusLabel)
                if let mv = battery.voltageMV {
                    row("电压", icon: "bolt.circle", value: String(format: "%.3f V", Double(mv) / 1000.0))
                }
                if let ma = battery.currentMA {
                    row("当前", icon: "arrow.left.arrow.right", value: "\(ma > 0 ? "+" : "")\(ma) mA")
                }
                if let mv = battery.voltageMV, let ma = battery.currentMA {
                    let watts = Double(mv) * Double(abs(ma)) / 1_000_000.0
                    row("功率", icon: "flame", value: String(format: "%.1f W", watts))
                }
                row("温度", icon: "thermometer.medium", value: String(format: "%.1f \u{00B0}C", battery.temperature))
                row("距离充满", icon: "battery.100.bolt", value: battery.charging == "charging" && battery.timeToFull > 0 ? formatETA(battery.timeToFull) : "—")
                row("剩余续航", icon: "battery.25", value: battery.charging == "discharging" && battery.timeToEmpty > 0 ? formatETA(battery.timeToEmpty) : "—")
            }
            .navigationTitle("电池详情")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func row(_ label: String, icon: String, value: String) -> some View {
        HStack {
            Label(label, systemImage: icon)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
    }

    private var statusLabel: String {
        switch battery.charging {
        case "stopped": return "已停止充电"
        case "charging": return battery.capacity >= 100 ? "已充满" : "充电中"
        default: return "放电中"
        }
    }

    private func formatETA(_ minutes: Int) -> String {
        if minutes >= 1440 {
            let d = minutes / 1440, h = (minutes % 1440) / 60, m = minutes % 60
            return "\(d)天 \(h)小时 \(m)分"
        }
        return minutes >= 60 ? "\(minutes / 60)小时 \(minutes % 60)分" : "\(minutes)分"
    }

    private func batteryIcon(_ percent: Int) -> String {
        if percent >= 75 { return "battery.100" }
        if percent >= 50 { return "battery.75" }
        if percent >= 25 { return "battery.50" }
        return "battery.25"
    }
}
