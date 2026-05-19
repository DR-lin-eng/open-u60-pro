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
    val logLevel: String = "",
    val ipv6: Boolean = false,
    val unifiedDelay: Boolean = false,
    val sniffing: Boolean = false,
    val geodataMode: Boolean = false,
    val geoUpdateInterval: Int = 0,
    val bindAddress: String = "",
    val interfaceName: String = "",
    val globalClientFingerprint: String = "",
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
    val latestDelayMs: Int? = null,
)

data class ClashProxyNode(
    val name: String = "",
    val type: String = "",
    val alive: Boolean = true,
    val current: String = "",
    val options: List<String> = emptyList(),
    val latestDelayMs: Int? = null,
)

data class ClashProxyProvider(
    val name: String = "",
    val type: String = "",
    val vehicleType: String = "",
    val updatedAt: String = "",
    val nodes: List<ClashProxyNode> = emptyList(),
) {
    val aliveCount: Int
        get() = nodes.count { it.alive }
}

data class ClashRuleProvider(
    val name: String = "",
    val behavior: String = "",
    val format: String = "",
    val ruleCount: Int = 0,
    val updatedAt: String = "",
)

data class ClashRule(
    val type: String = "",
    val payload: String = "",
    val proxy: String = "",
    val size: Int = -1,
)

data class ClashConnection(
    val id: String = "",
    val host: String = "",
    val destination: String = "",
    val source: String = "",
    val network: String = "",
    val type: String = "",
    val rule: String = "",
    val rulePayload: String = "",
    val chains: List<String> = emptyList(),
    val upload: Long = 0,
    val download: Long = 0,
    val startedAt: String = "",
    val process: String = "",
)

data class ClashLogEntry(
    val id: Long,
    val type: String,
    val payload: String,
)

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
            logLevel = configs["log-level"] as? String ?: "",
            ipv6 = DeviceParser.asBool(configs["ipv6"]),
            unifiedDelay = DeviceParser.asBool(configs["unified-delay"]),
            sniffing = DeviceParser.asBool(configs["sniffing"]),
            geodataMode = DeviceParser.asBool(configs["geodata-mode"]),
            geoUpdateInterval = DeviceParser.asInt(configs["geo-update-interval"]) ?: 0,
            bindAddress = configs["bind-address"] as? String ?: "",
            interfaceName = configs["interface-name"] as? String ?: "",
            globalClientFingerprint = configs["global-client-fingerprint"] as? String ?: "",
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
                latestDelayMs = extractLatestDelay(item),
            )
        }
    }

    fun parseProxyProviders(data: Map<String, Any?>): List<ClashProxyProvider> {
        val providers = data["providers"] as? Map<*, *> ?: return emptyList()
        return providers.mapNotNull { (rawName, rawValue) ->
            val name = rawName?.toString() ?: return@mapNotNull null
            val provider = rawValue as? Map<*, *> ?: return@mapNotNull null
            val nodes = (provider["proxies"] as? List<*>)?.mapNotNull { rawNode ->
                val node = rawNode as? Map<*, *> ?: return@mapNotNull null
                ClashProxyNode(
                    name = node["name"] as? String ?: "",
                    type = node["type"] as? String ?: "",
                    alive = DeviceParser.asBool(node["alive"]),
                    current = node["now"] as? String ?: "",
                    options = (node["all"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                    latestDelayMs = extractLatestDelay(node),
                )
            } ?: emptyList()
            ClashProxyProvider(
                name = name,
                type = provider["type"] as? String ?: "",
                vehicleType = provider["vehicleType"] as? String ?: "",
                updatedAt = provider["updatedAt"] as? String ?: "",
                nodes = nodes,
            )
        }
    }

    fun parseRuleProviders(data: Map<String, Any?>): List<ClashRuleProvider> {
        val providers = data["providers"] as? Map<*, *> ?: return emptyList()
        return providers.mapNotNull { (rawName, rawValue) ->
            val name = rawName?.toString() ?: return@mapNotNull null
            val provider = rawValue as? Map<*, *> ?: return@mapNotNull null
            ClashRuleProvider(
                name = name,
                behavior = provider["behavior"] as? String ?: "",
                format = provider["format"] as? String ?: "",
                ruleCount = DeviceParser.asInt(provider["ruleCount"]) ?: 0,
                updatedAt = provider["updatedAt"] as? String ?: "",
            )
        }
    }

    fun parseRules(data: Map<String, Any?>): List<ClashRule> {
        val rules = data["rules"] as? List<*> ?: return emptyList()
        return rules.mapNotNull { rawRule ->
            val rule = rawRule as? Map<*, *> ?: return@mapNotNull null
            ClashRule(
                type = rule["type"] as? String ?: "",
                payload = rule["payload"] as? String ?: "",
                proxy = rule["proxy"] as? String ?: "",
                size = DeviceParser.asInt(rule["size"]) ?: -1,
            )
        }
    }

    fun parseConnections(data: Map<String, Any?>): List<ClashConnection> {
        val connections = data["connections"] as? List<*> ?: return emptyList()
        return connections.mapNotNull { rawConnection ->
            val connection = rawConnection as? Map<*, *> ?: return@mapNotNull null
            val metadata = connection["metadata"] as? Map<*, *> ?: emptyMap<String, Any?>()
            val host = metadata["host"] as? String ?: ""
            val destinationIp = metadata["destinationIP"] as? String ?: ""
            val destinationPort = metadata["destinationPort"]?.toString().orEmpty()
            val sourceIp = metadata["sourceIP"] as? String ?: ""
            val sourcePort = metadata["sourcePort"]?.toString().orEmpty()
            ClashConnection(
                id = connection["id"] as? String ?: "",
                host = host,
                destination = listOfNotNull(destinationIp.takeIf { it.isNotBlank() }, destinationPort.takeIf { it.isNotBlank() })
                    .joinToString(":"),
                source = listOfNotNull(sourceIp.takeIf { it.isNotBlank() }, sourcePort.takeIf { it.isNotBlank() })
                    .joinToString(":"),
                network = metadata["network"] as? String ?: "",
                type = metadata["type"] as? String ?: "",
                rule = connection["rule"] as? String ?: "",
                rulePayload = connection["rulePayload"] as? String ?: "",
                chains = (connection["chains"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                upload = DeviceParser.asLong(connection["upload"]) ?: 0,
                download = DeviceParser.asLong(connection["download"]) ?: 0,
                startedAt = connection["start"] as? String ?: "",
                process = metadata["process"] as? String ?: "",
            )
        }
    }

    fun parseLogEntry(data: Map<String, Any?>, fallbackId: Long): ClashLogEntry {
        return ClashLogEntry(
            id = fallbackId,
            type = data["type"] as? String ?: "info",
            payload = data["payload"] as? String ?: "",
        )
    }

    fun parseDelay(data: Map<String, Any?>): Int? = DeviceParser.asInt(data["delay"])

    private fun extractLatestDelay(item: Map<*, *>): Int? {
        val historyDelay = (item["history"] as? List<*>)?.lastOrNull()
            ?.let { entry -> DeviceParser.asInt((entry as? Map<*, *>)?.get("delay")) }
            ?.takeIf { it > 0 }
        if (historyDelay != null) return historyDelay

        val extra = item["extra"] as? Map<*, *> ?: return null
        extra.values.forEach { rawValue ->
            val detail = rawValue as? Map<*, *> ?: return@forEach
            val delay = (detail["history"] as? List<*>)?.lastOrNull()
                ?.let { entry -> DeviceParser.asInt((entry as? Map<*, *>)?.get("delay")) }
                ?.takeIf { it > 0 }
            if (delay != null) return delay
        }
        return null
    }
}
