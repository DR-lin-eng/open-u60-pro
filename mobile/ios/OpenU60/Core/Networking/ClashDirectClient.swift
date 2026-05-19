import Foundation

@MainActor
final class ClashDirectClient {
    private static let defaultSecret = "123456"
    private static let defaultPort = 7788

    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 5
        config.timeoutIntervalForResource = 10
        session = URLSession(configuration: config)
    }

    func getJSON(agentBaseURL: String, path: String) async throws -> [String: Any] {
        let data = try await request(agentBaseURL: agentBaseURL, method: "GET", path: path, body: nil)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw AgentError.decodingError("预期为 JSON 对象")
        }
        return json
    }

    func putJSON(agentBaseURL: String, path: String, body: [String: Any]) async throws -> [String: Any] {
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let data = try await request(agentBaseURL: agentBaseURL, method: "PUT", path: path, body: bodyData)
        if data.isEmpty { return [:] }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw AgentError.decodingError("预期为 JSON 对象")
        }
        return json
    }

    func patchJSON(agentBaseURL: String, path: String, body: [String: Any]) async throws -> [String: Any] {
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let data = try await request(agentBaseURL: agentBaseURL, method: "PATCH", path: path, body: bodyData)
        if data.isEmpty { return [:] }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw AgentError.decodingError("预期为 JSON 对象")
        }
        return json
    }

    private func request(agentBaseURL: String, method: String, path: String, body: Data?) async throws -> Data {
        guard let agentURL = URL(string: agentBaseURL),
              let host = agentURL.host else {
            throw AgentError.serverUnreachable
        }
        var components = URLComponents()
        components.scheme = agentURL.scheme ?? "http"
        components.host = host
        components.port = Self.defaultPort
        components.percentEncodedPath = path

        guard let url = components.url else {
            throw AgentError.serverUnreachable
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(Self.defaultSecret)", forHTTPHeaderField: "Authorization")
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .timedOut {
            throw AgentError.timeout
        } catch let error as URLError where error.code == .cannotConnectToHost || error.code == .notConnectedToInternet {
            throw AgentError.serverError("无法连接 Clash 控制器，请确认 7788 端口可访问")
        } catch {
            throw AgentError.networkError(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AgentError.serverUnreachable
        }

        switch httpResponse.statusCode {
        case 200...299:
            return data
        case 401:
            throw AgentError.serverError("Clash API 鉴权失败，请检查 secret 是否仍为默认 123456")
        default:
            let message = String(data: data, encoding: .utf8) ?? "HTTP \(httpResponse.statusCode)"
            throw AgentError.serverError(message)
        }
    }
}
