package com.weaknet.simulator.auth

import java.util.UUID

data class AuthAccount(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String,
    val account: String,
    val secret: String,
    val digits: Int = 6,
    val period: Int = 30
) {
    val displayName: String
        get() = if (issuer.isNotBlank()) "$issuer ($account)" else account

    companion object {
        fun fromOtpauthUri(uri: String): AuthAccount? {
            if (!uri.startsWith("otpauth://totp/")) return null

            try {
                val withoutScheme = uri.removePrefix("otpauth://totp/")
                val pathEnd = withoutScheme.indexOf('?')
                if (pathEnd < 0) return null

                val label = java.net.URLDecoder.decode(withoutScheme.substring(0, pathEnd), "UTF-8")
                val params = withoutScheme.substring(pathEnd + 1)
                    .split("&")
                    .associate {
                        val (k, v) = it.split("=", limit = 2)
                        k.lowercase() to java.net.URLDecoder.decode(v, "UTF-8")
                    }

                val secret = params["secret"] ?: return null

                val issuer: String
                val account: String
                if (label.contains(":")) {
                    val parts = label.split(":", limit = 2)
                    issuer = params["issuer"] ?: parts[0].trim()
                    account = parts[1].trim()
                } else {
                    issuer = params["issuer"] ?: ""
                    account = label.trim()
                }

                return AuthAccount(
                    issuer = issuer,
                    account = account,
                    secret = secret,
                    digits = params["digits"]?.toIntOrNull() ?: 6,
                    period = params["period"]?.toIntOrNull() ?: 30
                )
            } catch (_: Exception) {
                return null
            }
        }
    }
}
