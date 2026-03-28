package fi.junixald.NutellaService

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

object CodeGenerator {
    private const val CODE_GRANULARITY_MS: Long = 1000 * 60

    fun generateFlCode(sharedSecret: String, customTimestamp: Long): String {
        val key = sharedSecret.trim()
        if (key.isEmpty()) return "000000"

        val timestampMs = customTimestamp * 1000
        val codeValidityMs = 1000L * 60 * 60 // 1 hour
        val interval = timestampMs / codeValidityMs
        val intervalBeginningTimestampMs = interval * codeValidityMs
        val adjustedTimestamp = intervalBeginningTimestampMs / CODE_GRANULARITY_MS
        
        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(adjustedTimestamp)
        val bigEndianTimestamp = buffer.array()

        val hmac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA1")
        hmac.init(secretKey)
        val digest = hmac.doFinal(bigEndianTimestamp)

        val offset = (digest.last() and 0xf).toInt()
        val result = ByteBuffer.wrap(digest, offset, 4).int and 0x7fffffff

        val code = result % 1000000
        return String.format("%06d", code)
    }

    fun remainingTime(customTimestamp: Long): Long {
        val timestampMs = customTimestamp * 1000
        val codeValidityMs = 1000L * 60 * 60 // 1 hour
        val interval = timestampMs / codeValidityMs
        val intervalBeginningTimestampMs = interval * codeValidityMs
        val validToMs = intervalBeginningTimestampMs + codeValidityMs

        return (validToMs - timestampMs) / 1000
    }
}
