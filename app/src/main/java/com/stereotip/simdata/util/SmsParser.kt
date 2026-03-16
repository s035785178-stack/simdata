package com.stereotip.simdata.util

import com.stereotip.simdata.data.BalanceResult

object SmsParser {

    private val lineRegex =
        Regex("""Your\s*number\s*is\s*:?\s*([0-9+]+)""", RegexOption.IGNORE_CASE)

    private val dataInternetRegex =
        Regex("""Data\s*Internet\s*:?\s*([0-9.]+)""", RegexOption.IGNORE_CASE)

    private val yourBalanceRegex =
        Regex("""Your\s*Balance\s*:?\s*([0-9.]+)""", RegexOption.IGNORE_CASE)

    private val validRegex =
        Regex("""Valid\s*:?\s*([0-9.\-\/]+)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): BalanceResult? {
        val line = lineRegex.find(text)?.groupValues?.getOrNull(1)?.trim()

        val dataInternet = dataInternetRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        val yourBalance = yourBalanceRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        val valid = validRegex.find(text)?.groupValues?.getOrNull(1)?.trim()

        val chosenMb = chooseBestBalance(dataInternet, yourBalance)

        if (chosenMb == null && line.isNullOrBlank() && valid.isNullOrBlank()) {
            return null
        }

        return BalanceResult(
            lineNumber = line,
            dataMb = chosenMb,
            validUntil = valid,
            rawMessage = text
        )
    }

    private fun chooseBestBalance(dataInternet: String?, yourBalance: String?): Int? {
        val dataNum = dataInternet?.toDoubleOrNull()
        val balanceNum = yourBalance?.toDoubleOrNull()

        return when {
            dataNum != null && dataNum >= 500 -> dataNum.toInt()
            balanceNum != null && balanceNum >= 500 -> balanceNum.toInt()
            dataNum != null -> dataNum.toInt()
            balanceNum != null -> balanceNum.toInt()
            else -> null
        }
    }
}
