package com.molina.core.security

/** Pecah "host", "host:port", atau "[v6]:port" menjadi host dan port; null bila tidak sah. */
internal fun parseHostPort(s: String, defaultPort: Int): Pair<String, Int>? {
    if (s.isEmpty()) return null
    val host: String
    val portText: String
    if (s.startsWith("[")) {
        val end = s.indexOf(']')
        if (end < 0) return null
        host = s.substring(1, end)
        val rest = s.substring(end + 1)
        portText = when {
            rest.isEmpty() -> ""
            rest.startsWith(":") -> rest.substring(1)
            else -> return null
        }
    } else {
        val c = s.lastIndexOf(':')
        if (c >= 0) { host = s.substring(0, c); portText = s.substring(c + 1) } else { host = s; portText = "" }
    }
    if (host.isEmpty()) return null
    val port = if (portText.isEmpty()) defaultPort else (portText.toIntOrNull() ?: return null)
    if (port !in 1..65535) return null
    return host to port
}
