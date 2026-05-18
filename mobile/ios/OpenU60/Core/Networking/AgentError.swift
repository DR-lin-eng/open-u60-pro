import Foundation

enum AgentError: LocalizedError {
    case unauthorized
    case serverError(String)
    case networkError(Error)
    case decodingError(String)
    case serverUnreachable
    case timeout

    var errorDescription: String? {
        switch self {
        case .unauthorized:
            return "未认证，请先登录。"
        case .serverError(let message):
            return "服务器错误：\(message)"
        case .networkError(let error):
            return "网络错误：\(error.localizedDescription)"
        case .decodingError(let detail):
            return "解析响应失败：\(detail)"
        case .serverUnreachable:
            return "无法连接到代理"
        case .timeout:
            return "请求超时"
        }
    }
}
