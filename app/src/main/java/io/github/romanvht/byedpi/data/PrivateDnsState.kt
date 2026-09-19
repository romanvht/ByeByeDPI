package io.github.romanvht.byedpi.data

sealed class PrivateDnsState {
    data class Configured(val hostname: String) : PrivateDnsState()
    data object Inactive : PrivateDnsState()
    data object Unknown : PrivateDnsState()
    data object Unsupported : PrivateDnsState()
}
