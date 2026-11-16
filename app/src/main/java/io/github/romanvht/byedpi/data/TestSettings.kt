package io.github.romanvht.byedpi.data

data class TestSettings(
    val sites: List<String>,
    val commands: List<String>,
    val host: String,
    val port: Int,
    val delaySec: Int,
    val requestsCount: Int,
    val requestTimeout: Long,
    val requestLimit: Int,
)

fun parseTestCommands(content: String, sni: String): List<String> =
    content.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.replace("{sni}", "\"$sni\"") }
        .toList()
