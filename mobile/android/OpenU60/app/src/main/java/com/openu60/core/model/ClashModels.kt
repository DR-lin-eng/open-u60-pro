package com.openu60.core.model

data class ClashStatus(
    val running: Boolean = false,
    val version: String = "",
    val mode: String = "",
    val controllerPort: Int = 7788,
    val ui: String = "",
    val mixedPort: Int = 0,
    val redirPort: Int = 0,
    val tproxyPort: Int = 0,
    val allowLan: Boolean = false,
    val tunEnabled: Boolean = false,
    val connections: Int = 0,
    val downloadTotal: Long = 0,
    val uploadTotal: Long = 0,
) {
    companion object {
        val empty = ClashStatus()
    }

    val modeLabel: String
        get() = when (mode.lowercase()) {
            "rule" -> "规则"
            "global" -> "全局"
            "direct" -> "直连"
            else -> "--"
        }
}

data class ClashSelectorGroup(
    val name: String = "",
    val current: String = "",
    val options: List<String> = emptyList(),
    val alive: Boolean = true,
) {
    companion object {
        val empty = ClashSelectorGroup()
    }
}

object ClashParser {
    fun offlineStatus(): ClashStatus = ClashStatus.empty

    fun parseStatus(
        version: Map<String, Any?>,
        configs: Map<String, Any?>,
        connections: Map<String, Any?>,
    ): ClashStatus {
        return ClashStatus(
            running = true,
            version = version["version"] as? String ?: "",
            mode = configs["mode"] as? String ?: "",
            controllerPort = 7788,
            ui = "zashboard",
            mixedPort = DeviceParser.asInt(configs["mixed-port"]) ?: 0,
            redirPort = DeviceParser.asInt(configs["redir-port"]) ?: 0,
            tproxyPort = DeviceParser.asInt(configs["tproxy-port"]) ?: 0,
            allowLan = DeviceParser.asBool(configs["allow-lan"]),
            tunEnabled = DeviceParser.asBool((configs["tun"] as? Map<*, *>)?.get("enable")),
            connections = (connections["connections"] as? List<*>)?.size ?: 0,
            downloadTotal = DeviceParser.asLong(connections["downloadTotal"]) ?: 0,
            uploadTotal = DeviceParser.asLong(connections["uploadTotal"]) ?: 0,
        )
    }

    fun parseSelectors(data: Map<String, Any?>): List<ClashSelectorGroup> {
        val proxies = data["proxies"] as? Map<*, *> ?: return emptyList()
        return proxies.mapNotNull { (rawName, rawValue) ->
            val name = rawName?.toString() ?: return@mapNotNull null
            val item = rawValue as? Map<*, *> ?: return@mapNotNull null
            if ((item["type"] as? String) != "Selector") return@mapNotNull null
            val options = (item["all"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            if (options.isEmpty()) return@mapNotNull null
            ClashSelectorGroup(
                name = name,
                current = item["now"] as? String ?: "",
                options = options,
                alive = DeviceParser.asBool(item["alive"]),
            )
        }.sortedBy { it.name }
    }
}
