package com.stereotip.simdata.util

import com.stereotip.simdata.data.BalanceResult

object SmsParser {
    private val numberRegex = Regex("Your\\s+number\\s+is:\\s*([0-9+]+)", RegexOption.IGNORE_CASE)
    private val dataRegex = Regex("Data\\s+Internet\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val validRegex = Regex("Valid\\s*:\\s*([0-9.\\-/]+)", RegexOption.IGNORE_CASE)

    fun parse(text: String): BalanceResult? {
        if (!text.contains("Data Internet", true) && !text.contains("Your number is", true)) return null
        val line = numberRegex.find(text)?.groupValues?.getOrNull(1)
        val mb = dataRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val valid = validRegex.find(text)?.groupValues?.getOrNull(1)
        return BalanceResult(lineNumber = line, dataMb = mb, validUntil = valid, rawMessage = text)
    }
}
