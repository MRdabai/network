package com.weaknet.simulator.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpGenerator {

    fun generateCode(secret: String, timeMillis: Long = System.currentTimeMillis(), digits: Int = 6, period: Int = 30): String {
        val key = base32Decode(secret)
        val counter = timeMillis / 1000 / period
        val hash = hmacSha1(key, counterToBytes(counter))
        val code = truncate(hash, digits)
        return code.toString().padStart(digits, '0')
    }

    fun getRemainingSeconds(period: Int = 30): Int {
        val elapsed = (System.currentTimeMillis() / 1000 % period).toInt()
        return period - elapsed
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }

    private fun counterToBytes(counter: Long): ByteArray {
        val data = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            data[i] = (value and 0xFF).toByte()
            value = value shr 8
        }
        return data
    }

    private fun truncate(hash: ByteArray, digits: Int): Int {
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return binary % 10.0.pow(digits).toInt()
    }

    private fun base32Decode(encoded: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = encoded.uppercase().replace(" ", "").replace("-", "").trimEnd('=')

        val output = mutableListOf<Byte>()
        var buffer = 0L
        var bitsLeft = 0

        for (c in cleaned) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add((buffer shr bitsLeft and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }
}
