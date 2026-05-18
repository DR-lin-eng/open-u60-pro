import SwiftUI

enum PinSheetAction {
    case verify        // unlock PIN-locked SIM
    case enableLock    // enable PIN lock on SIM
    case disableLock   // disable PIN lock on SIM
}

@Observable
@MainActor
final class SIMViewModel {
    var simInfo: SIMInfo = .empty
    var lockInfo: SIMLockInfo = .empty
    var isLoading: Bool = false
    var message: String?
    var messageIsError: Bool = false

    // Sheet state
    var showChangePinSheet: Bool = false
    var showEnterPinSheet: Bool = false
    var showEnterPukSheet: Bool = false
    var showUnlockSheet: Bool = false
    var pinSheetAction: PinSheetAction = .verify

    // Form fields
    var pinInput: String = ""
    var oldPinInput: String = ""
    var newPinInput: String = ""
    var pukInput: String = ""
    var nckInput: String = ""

    private let client: AgentClient
    private let authManager: AuthManager

    init(client: AgentClient, authManager: AuthManager) {
        self.client = client
        self.authManager = authManager
    }

    var isPinEnabled: Bool {
        simInfo.pinStatus == "1"
    }

    var isPinLocked: Bool {
        let sim = simInfo.simStatus.lowercased()
        let modem = simInfo.modemMainState.lowercased()
        return sim == "wait pin" || modem == "modem_waitpin"
    }

    var isPukLocked: Bool {
        let sim = simInfo.simStatus.lowercased()
        let modem = simInfo.modemMainState.lowercased()
        return sim == "wait puk" || modem == "modem_waitpuk"
    }

    func submitPin() async {
        switch pinSheetAction {
        case .verify:
            await verifyPin()
        case .enableLock:
            await changePinMode(enable: true)
            showEnterPinSheet = false
        case .disableLock:
            await changePinMode(enable: false)
            showEnterPinSheet = false
        }
    }

    func refresh() async {
        isLoading = true
        message = nil

        async let simTask = fetchSIMInfo()
        async let lockTask = fetchSIMLock()

        let (sim, lock) = await (simTask, lockTask)
        if let sim { simInfo = sim }
        if let lock { lockInfo = lock }

        isLoading = false
    }

    func changePinMode(enable: Bool) async {
        isLoading = true

        do {
            let _ = try await client.postJSON("/api/sim/pin/mode", body: [
                    "pin_num_m": pinInput,
                    "pin_mode": enable ? 1 : 0,
                    "pin_encode_flag": "0"
                ])
            pinInput = ""
            showMessage(enable ? "PIN 锁已启用" : "PIN 锁已禁用", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }

        isLoading = false
    }

    func changePin() async {
        guard oldPinInput.count >= 4, newPinInput.count >= 4 else {
            showMessage("PIN 至少需要 4 位数字", isError: true)
            return
        }

        isLoading = true

        do {
            let _ = try await client.postJSON("/api/sim/pin/change", body: [
                    "pin_num": oldPinInput,
                    "new_pin_num": newPinInput,
                    "pin_encode_flag": "0"
                ])
            oldPinInput = ""
            newPinInput = ""
            showChangePinSheet = false
            showMessage("PIN 修改成功", isError: false)
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }

        isLoading = false
    }

    func verifyPin() async {
        guard pinInput.count >= 4 else {
            showMessage("PIN 至少需要 4 位数字", isError: true)
            return
        }

        isLoading = true

        do {
            let _ = try await client.postJSON("/api/sim/pin/verify", body: [
                    "pin_num": pinInput,
                    "puk_num": "",
                    "pin_encode_flag": "0"
                ])
            pinInput = ""
            showEnterPinSheet = false
            showMessage("PIN 验证成功", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }

        isLoading = false
    }

    func verifyPuk() async {
        guard pukInput.count >= 8 else {
            showMessage("PUK 至少需要 8 位数字", isError: true)
            return
        }
        guard newPinInput.count >= 4 else {
            showMessage("新 PIN 至少需要 4 位数字", isError: true)
            return
        }

        isLoading = true

        do {
            let _ = try await client.postJSON("/api/sim/pin/verify", body: [
                    "pin_num": newPinInput,
                    "puk_num": pukInput,
                    "pin_encode_flag": "0"
                ])
            pukInput = ""
            newPinInput = ""
            showEnterPukSheet = false
            showMessage("PUK 验证成功，新 PIN 已设置", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }

        isLoading = false
    }

    func unlockSIM() async {
        guard !nckInput.isEmpty else {
            showMessage("需要输入解锁码", isError: true)
            return
        }

        isLoading = true

        do {
            let _ = try await client.postJSON("/api/sim/unlock", body: ["nck": nckInput])
            nckInput = ""
            showUnlockSheet = false
            showMessage("SIM 解锁成功", isError: false)
            await refresh()
        } catch {
            showMessage("失败：\(error.localizedDescription)", isError: true)
        }

        isLoading = false
    }

    // MARK: - Private

    private func fetchSIMInfo() async -> SIMInfo? {
        do {
            let data = try await client.getJSON("/api/sim/info")
            return SIMParser.parseSIMInfo(data)
        } catch {
            showMessage("加载 SIM 信息失败：\(error.localizedDescription)", isError: true)
            return nil
        }
    }

    private func fetchSIMLock() async -> SIMLockInfo? {
        do {
            let data = try await client.getJSON("/api/sim/lock-trials")
            return SIMParser.parseSIMLock(data)
        } catch {
            return nil
        }
    }

    private func showMessage(_ text: String, isError: Bool) {
        message = text
        messageIsError = isError
    }
}
