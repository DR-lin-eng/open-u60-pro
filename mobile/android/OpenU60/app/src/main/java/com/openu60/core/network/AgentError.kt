package com.openu60.core.network

sealed class AgentError(override val message: String, override val cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized : AgentError("未认证，请先登录。")
    class ServerError(msg: String) : AgentError("服务器错误：$msg")
    class NetworkError(msg: String, cause: Throwable? = null) : AgentError("网络错误：$msg", cause)
    class DecodingError(msg: String) : AgentError("解析响应失败：$msg")
    class ServerUnreachable : AgentError("无法连接到代理")
    class Timeout : AgentError("请求超时")
}
