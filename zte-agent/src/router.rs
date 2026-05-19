use std::process::Command;

use serde_json::{json, Value};

use crate::handlers::AppState;
use crate::ubus;

const DOMAIN_FILTER_DEVS: [&str; 4] = ["lan", "lan2", "lan3", "lan4"];

#[derive(Clone)]
struct DomainFilterRuleEntry {
    dev: String,
    index: usize,
    domain: String,
    enabled: bool,
}

fn value_to_toggle(value: &Value, default: &str) -> String {
    match value {
        Value::String(s) => {
            if s == "1" || s.eq_ignore_ascii_case("true") || s.eq_ignore_ascii_case("on") {
                "1".to_string()
            } else {
                "0".to_string()
            }
        }
        Value::Bool(b) => {
            if *b {
                "1".to_string()
            } else {
                "0".to_string()
            }
        }
        Value::Number(n) => {
            if n.as_i64().unwrap_or(0) != 0 {
                "1".to_string()
            } else {
                "0".to_string()
            }
        }
        _ => default.to_string(),
    }
}

fn read_uci_value(keys: &[&str], default: &str) -> String {
    keys.iter()
        .find_map(|key| ubus::uci_get(key).ok())
        .unwrap_or_else(|| default.to_string())
}

fn read_domain_filter_rules() -> Vec<DomainFilterRuleEntry> {
    let mut rules = Vec::new();
    for dev in DOMAIN_FILTER_DEVS {
        let count_key = format!("zwrt_router.domain_filter_{dev}.count");
        let count = ubus::uci_get(&count_key)
            .ok()
            .and_then(|v| v.parse::<usize>().ok())
            .unwrap_or(0);
        for index in 0..count {
            let rule_key = format!("zwrt_router.domain_filter_{dev}.rule{index}");
            let Some(rule) = ubus::uci_get(&rule_key).ok() else {
                continue;
            };
            let mut parts = rule.splitn(2, ',');
            let domain = parts.next().unwrap_or("").trim().to_string();
            if domain.is_empty() {
                continue;
            }
            let enabled = parts.next().unwrap_or("1").trim() == "1";
            rules.push(DomainFilterRuleEntry {
                dev: dev.to_string(),
                index,
                domain,
                enabled,
            });
        }
    }
    rules
}

fn parse_domain_filter_id(id: &str) -> Option<(String, usize)> {
    if let Some((dev, index)) = id.split_once(':') {
        return index.parse::<usize>().ok().map(|i| (dev.to_string(), i));
    }
    id.parse::<usize>().ok().map(|i| ("lan".to_string(), i))
}

fn apply_domain_filter() -> Result<(), String> {
    let output = Command::new("sh")
        .args(["/sbin/domain_filter_op.sh", "init"])
        .output()
        .map_err(|e| format!("domain_filter_op.sh: {e}"))?;
    if !output.status.success() {
        return Err(format!(
            "domain_filter_op.sh init: {}",
            String::from_utf8_lossy(&output.stderr).trim()
        ));
    }
    Ok(())
}

pub fn router_dns_get(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_router.api", "router_get_dns_para", Some("{}")) {
        Ok(data) => {
            // Firmware returns keys with "wan_" prefix (e.g. wan_dns_mode);
            // strip it so iOS client can use clean names (dns_mode, prefer_dns_manual, etc.)
            let mut cleaned = serde_json::Map::new();
            if let Some(obj) = data.as_object() {
                for (k, v) in obj {
                    let key = k.strip_prefix("wan_").unwrap_or(k).to_string();
                    cleaned.insert(key, v.clone());
                }
            }
            // Firmware bug: sometimes returns empty manual DNS values; fill from UCI
            if cleaned.get("dns_mode").and_then(|v| v.as_str()) == Some("manual") {
                if cleaned
                    .get("prefer_dns_manual")
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .is_empty()
                {
                    if let Ok(v) = ubus::uci_get("network.wan.dns") {
                        let mut parts = v.split_whitespace();
                        if let Some(primary) = parts.next() {
                            cleaned.insert("prefer_dns_manual".into(), Value::String(primary.to_string()));
                        }
                        if let Some(secondary) = parts.next() {
                            cleaned.insert("standby_dns_manual".into(), Value::String(secondary.to_string()));
                        }
                    }
                }
            }
            (200, json!({"ok": true, "data": Value::Object(cleaned)}))
        }
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_dns_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_wan_dns", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_lan_get(_state: &AppState) -> (u16, Value) {
    let ip = ubus::uci_get("network.lan.ipaddr").unwrap_or_default();
    let mask = ubus::uci_get("network.lan.netmask").unwrap_or_default();
    let ignore = ubus::uci_get("dhcp.lan.ignore").unwrap_or_default();
    let start = ubus::uci_get("dhcp.lan.start").unwrap_or_default();
    let limit = ubus::uci_get("dhcp.lan.limit").unwrap_or_default();
    let lease = ubus::uci_get("dhcp.lan.leasetime").unwrap_or_default();
    let end = compute_dhcp_end(&ip, &start, &limit);
    (200, json!({"ok": true, "data": {
        "lan_ipaddr": ip, "lan_netmask": mask,
        "dhcp_enable": if ignore == "1" { "0" } else { "1" },
        "dhcp_start": start, "dhcp_end": end,
        "dhcp_lease_time": lease
    }}))
}

fn compute_dhcp_end(base_ip: &str, start: &str, limit: &str) -> String {
    let start_num: u32 = start.parse().unwrap_or(100);
    let limit_num: u32 = limit.parse().unwrap_or(50);
    let end_host = start_num + limit_num - 1;
    // Replace last octet of base IP with end_host
    if let Some(prefix) = base_ip.rfind('.') {
        format!("{}.{end_host}", &base_ip[..prefix])
    } else {
        format!("192.168.0.{end_host}")
    }
}

pub fn router_lan_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_lan_para", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_get(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_router.api", "router_get_firewall_para", Some("{}")) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_switch_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_firewall_switch", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_level_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_firewall_level", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_nat_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_nat_switch", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_dmz_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_dmz", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_upnp_get(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_router.api", "router_get_upnp", Some("{}")) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_upnp_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    let enable_upnp = parsed["upnp_switch"]
        .as_str()
        .and_then(|s| s.parse::<i64>().ok())
        .or_else(|| parsed["enable_upnp"].as_i64())
        .unwrap_or(0);
    let params = json!({
        "enable_upnp": enable_upnp,
        "notify_interval": parsed["notify_interval"].as_i64().unwrap_or(60),
        "ttl": parsed["ttl"].as_i64().unwrap_or(0),
        "natpmp": parsed["natpmp"].as_i64().unwrap_or(0)
    });
    match ubus::call("zwrt_router.api", "router_set_upnp_switch", Some(&params.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_port_forward_get(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_router.api", "router_get_portforward_rule", Some("{}")) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_port_forward_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_portforward", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_port_forward_switch(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_router.api", "router_set_portforward_switch", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_firewall_filter_rules(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_router.api", "router_get_macipport_filter_rule", Some("{}")) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_vpn_get(_state: &AppState) -> (u16, Value) {
    let mut obj = ubus::call("zwrt_router.api", "router_get_alg_para", Some("{}"))
        .ok()
        .and_then(|data| data.as_object().cloned())
        .unwrap_or_default();

    let l2tp = read_uci_value(
        &[
            "zwrt_router.alg.alg_l2tp_enable",
            "zwrt_router.@zudata_alg[0].alg_l2tp_enable",
        ],
        "0",
    );
    let pptp = read_uci_value(
        &[
            "zwrt_router.alg.alg_pptp_enable",
            "zwrt_router.@zudata_alg[0].alg_pptp_enable",
        ],
        "0",
    );
    let ipsec = read_uci_value(
        &[
            "zwrt_router.alg.alg_ipsec_enable",
            "zwrt_router.@zudata_alg[0].alg_ipsec_enable",
        ],
        "0",
    );
    let sip = read_uci_value(
        &[
            "zwrt_router.alg.alg_sip_enable",
            "zwrt_router.@zudata_alg[0].alg_sip_enable",
        ],
        "0",
    );
    let passthrough = read_uci_value(
        &[
            "zwrt_router.alg.vpnpassthr_enable",
            "zwrt_router.@zudata_alg[0].vpnpassthr_enable",
        ],
        "0",
    );

    obj.insert("l2tp_passthrough".into(), Value::String(l2tp));
    obj.insert("pptp_passthrough".into(), Value::String(pptp));
    obj.insert("ipsec_passthrough".into(), Value::String(ipsec));
    obj.insert("alg_sip_enable".into(), Value::String(sip));
    obj.insert("vpnpassthr_enable".into(), Value::String(passthrough));

    (200, json!({"ok": true, "data": Value::Object(obj)}))
}

pub fn router_vpn_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };

    let l2tp = value_to_toggle(
        parsed.get("l2tp_passthrough").unwrap_or(&Value::Null),
        &read_uci_value(
            &[
                "zwrt_router.alg.alg_l2tp_enable",
                "zwrt_router.@zudata_alg[0].alg_l2tp_enable",
            ],
            "0",
        ),
    );
    let pptp = value_to_toggle(
        parsed.get("pptp_passthrough").unwrap_or(&Value::Null),
        &read_uci_value(
            &[
                "zwrt_router.alg.alg_pptp_enable",
                "zwrt_router.@zudata_alg[0].alg_pptp_enable",
            ],
            "0",
        ),
    );
    let ipsec = value_to_toggle(
        parsed.get("ipsec_passthrough").unwrap_or(&Value::Null),
        &read_uci_value(
            &[
                "zwrt_router.alg.alg_ipsec_enable",
                "zwrt_router.@zudata_alg[0].alg_ipsec_enable",
            ],
            "0",
        ),
    );
    let sip = value_to_toggle(
        parsed.get("alg_sip_enable").unwrap_or(&Value::Null),
        &read_uci_value(
            &[
                "zwrt_router.alg.alg_sip_enable",
                "zwrt_router.@zudata_alg[0].alg_sip_enable",
            ],
            "0",
        ),
    );
    let passthrough = if l2tp == "1" || pptp == "1" || ipsec == "1" {
        "1".to_string()
    } else {
        read_uci_value(
            &[
                "zwrt_router.alg.vpnpassthr_enable",
                "zwrt_router.@zudata_alg[0].vpnpassthr_enable",
            ],
            "0",
        )
    };

    for (key, value) in [
        ("zwrt_router.alg.alg_l2tp_enable", l2tp.as_str()),
        ("zwrt_router.alg.alg_pptp_enable", pptp.as_str()),
        ("zwrt_router.alg.alg_ipsec_enable", ipsec.as_str()),
        ("zwrt_router.alg.alg_sip_enable", sip.as_str()),
        ("zwrt_router.alg.vpnpassthr_enable", passthrough.as_str()),
    ] {
        if let Err(e) = ubus::uci_set_no_commit(key, value) {
            return (503, json!({"ok": false, "error": e}));
        }
    }
    if let Err(e) = ubus::uci_commit("zwrt_router") {
        return (503, json!({"ok": false, "error": e}));
    }

    let params = json!({
        "alg_l2tp_enable": l2tp.parse::<i64>().unwrap_or(0),
        "alg_pptp_enable": pptp.parse::<i64>().unwrap_or(0),
        "alg_ipsec_enable": ipsec.parse::<i64>().unwrap_or(0),
        "alg_sip_enable": sip.parse::<i64>().unwrap_or(0)
    });
    match ubus::call("zwrt_router.api", "router_set_alg_switch", Some(&params.to_string())) {
        Ok(_) => router_vpn_get(_state),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_qos_get(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_router.api", "router_get_qos", Some("{}")) {
        Ok(data) => {
            let mut obj = data.as_object().cloned().unwrap_or_default();
            if !obj.contains_key("qos_smart_switch") {
                let fallback = ubus::uci_get("zwrt_router.qos.qos_smart_switch")
                    .or_else(|_| ubus::uci_get("zwrt_router.@zudata_qos[0].qos_smart_switch"))
                    .unwrap_or_else(|_| "0".to_string());
                obj.insert("qos_smart_switch".into(), Value::String(fallback));
            }
            if let Some(value) = obj.get("qos_smart_switch").cloned() {
                obj.insert("qos_switch".into(), value);
            }
            (200, json!({"ok": true, "data": Value::Object(obj)}))
        }
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_qos_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    let switch = parsed["qos_switch"]
        .as_str()
        .and_then(|s| s.parse::<i64>().ok())
        .or_else(|| parsed["qos_smart_switch"].as_i64())
        .unwrap_or(0);
    let params = json!({
        "qos_smart_switch": switch,
        "upload_total_limit_rate": parsed["upload_total_limit_rate"].as_i64().unwrap_or(0),
        "upload_total_limit_unit": parsed["upload_total_limit_unit"].as_i64().unwrap_or(0),
        "download_total_limit_rate": parsed["download_total_limit_rate"].as_i64().unwrap_or(0),
        "download_total_limit_unit": parsed["download_total_limit_unit"].as_i64().unwrap_or(0),
        "qos_smart_pri_type": parsed["qos_smart_pri_type"].as_i64().unwrap_or(0)
    });
    match ubus::call("zwrt_router.api", "router_set_qos", Some(&params.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_domain_filter_get(_state: &AppState) -> (u16, Value) {
    let enabled = read_uci_value(&["zwrt_router.domain_filter.enable"], "0");
    let policy = read_uci_value(&["zwrt_router.firewall.domain_filter_policy"], "black_list");
    let rules = read_domain_filter_rules()
        .into_iter()
        .map(|rule| {
            let target = if policy == "white_list" { "ACCEPT" } else { "DROP" };
            json!({
                "id": format!("{}:{}", rule.dev, rule.index),
                "domain": rule.domain,
                "fqdn": rule.domain,
                "enabled": if rule.enabled { "1" } else { "0" },
                "enable": if rule.enabled { "1" } else { "0" },
                "dev": rule.dev,
                "target": target
            })
        })
        .collect::<Vec<_>>();
    (
        200,
        json!({"ok": true, "data": {
            "enable": enabled,
            "policy": policy,
            "one_line": "1",
            "rule_list": rules
        }}),
    )
}

pub fn router_domain_filter_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };

    let action = parsed["action"].as_str().unwrap_or("");
    let policy = parsed["policy"]
        .as_str()
        .unwrap_or("black_list")
        .to_string();
    let default_enable = if action == "delete" { "0" } else { "1" };
    let enabled = value_to_toggle(parsed.get("enable").unwrap_or(&Value::Null), default_enable);

    if let Err(e) = ubus::uci_set_no_commit("zwrt_router.domain_filter.one_line", "1") {
        return (503, json!({"ok": false, "error": e}));
    }
    if let Err(e) = ubus::uci_set_no_commit("zwrt_router.firewall.domain_filter_policy", &policy) {
        return (503, json!({"ok": false, "error": e}));
    }

    match action {
        "" => {
            if let Err(e) = ubus::uci_set_no_commit("zwrt_router.domain_filter.enable", &enabled) {
                return (503, json!({"ok": false, "error": e}));
            }
        }
        "add" => {
            let domain = parsed["domain"]
                .as_str()
                .or_else(|| parsed["fqdn"].as_str())
                .unwrap_or("")
                .trim()
                .to_string();
            if domain.is_empty() {
                return (400, json!({"ok": false, "error": "domain is required"}));
            }
            let dev = parsed["dev"].as_str().unwrap_or("lan");
            let count_key = format!("zwrt_router.domain_filter_{dev}.count");
            let section_key = format!("zwrt_router.domain_filter_{dev}");
            let count = ubus::uci_get(&count_key)
                .ok()
                .and_then(|v| v.parse::<usize>().ok())
                .unwrap_or(0);
            let existing = read_domain_filter_rules()
                .into_iter()
                .find(|rule| rule.dev == dev && rule.domain.eq_ignore_ascii_case(&domain));
            let index = existing.as_ref().map(|rule| rule.index).unwrap_or(count);
            let rule_key = format!("zwrt_router.domain_filter_{dev}.rule{index}");

            if let Err(e) = ubus::uci_set_no_commit("zwrt_router.domain_filter.enable", "1") {
                return (503, json!({"ok": false, "error": e}));
            }
            if count == 0 && existing.is_none() {
                let _ = ubus::uci_set_no_commit(&section_key, "domain_filter_one_line");
            }
            if let Err(e) = ubus::uci_set_no_commit(&rule_key, &format!("{domain},{}", enabled)) {
                return (503, json!({"ok": false, "error": e}));
            }
            if existing.is_none() {
                if let Err(e) = ubus::uci_set_no_commit(&count_key, &(count + 1).to_string()) {
                    return (503, json!({"ok": false, "error": e}));
                }
            }
        }
        "delete" => {
            let rules = read_domain_filter_rules();
            let target = parsed["id"]
                .as_str()
                .and_then(parse_domain_filter_id)
                .or_else(|| {
                    let domain = parsed["domain"]
                        .as_str()
                        .or_else(|| parsed["fqdn"].as_str())
                        .unwrap_or("");
                    rules.iter()
                        .find(|rule| rule.domain.eq_ignore_ascii_case(domain))
                        .map(|rule| (rule.dev.clone(), rule.index))
                });
            let Some((dev, index)) = target else {
                return (404, json!({"ok": false, "error": "rule not found"}));
            };

            let count_key = format!("zwrt_router.domain_filter_{dev}.count");
            let count = ubus::uci_get(&count_key)
                .ok()
                .and_then(|v| v.parse::<usize>().ok())
                .unwrap_or(0);
            if index >= count {
                return (404, json!({"ok": false, "error": "rule not found"}));
            }

            for i in index..count.saturating_sub(1) {
                let next_key = format!("zwrt_router.domain_filter_{dev}.rule{}", i + 1);
                let current_key = format!("zwrt_router.domain_filter_{dev}.rule{i}");
                let next_value = ubus::uci_get(&next_key).unwrap_or_default();
                if let Err(e) = ubus::uci_set_no_commit(&current_key, &next_value) {
                    return (503, json!({"ok": false, "error": e}));
                }
            }

            let last_key = format!("zwrt_router.domain_filter_{dev}.rule{}", count.saturating_sub(1));
            if count > 0 {
                if let Err(e) = ubus::uci_delete(&last_key) {
                    return (503, json!({"ok": false, "error": e}));
                }
            }
            if let Err(e) = ubus::uci_set_no_commit(&count_key, &count.saturating_sub(1).to_string()) {
                return (503, json!({"ok": false, "error": e}));
            }
        }
        _ => return (400, json!({"ok": false, "error": "unsupported action"})),
    }

    if let Err(e) = ubus::uci_commit("zwrt_router") {
        return (503, json!({"ok": false, "error": e}));
    }
    if let Err(e) = apply_domain_filter() {
        return (503, json!({"ok": false, "error": e}));
    }
    router_domain_filter_get(_state)
}

pub fn router_apn_mode_get(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_apn_object", "get_apn_mode", Some("{}")) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_apn_mode_set(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_apn_object", "set_apn_mode", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_apn_profiles_get(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_apn_object", "get_manu_apn_list", Some("{}")) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_apn_profiles_add(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_apn_object", "add_manu_apn", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_apn_profiles_modify(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_apn_object", "modify_manu_apn", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_apn_auto_profiles(_state: &AppState) -> (u16, Value) {
    match ubus::call("zwrt_apn_object", "get_auto_apn_list", Some("{}")) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_apn_profiles_delete(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_apn_object", "delete_manu_apn", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}

pub fn router_apn_profiles_activate(_state: &AppState, body: &[u8]) -> (u16, Value) {
    let parsed: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return (400, json!({"ok": false, "error": "invalid JSON"})),
    };
    match ubus::call("zwrt_apn_object", "enable_manu_apn_id", Some(&parsed.to_string())) {
        Ok(data) => (200, json!({"ok": true, "data": data})),
        Err(e) => (503, json!({"ok": false, "error": e})),
    }
}
