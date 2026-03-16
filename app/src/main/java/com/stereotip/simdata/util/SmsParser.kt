package com.stereotip.simdata.util

import com.stereotip.simdata.data.BalanceResult

object SmsParser {
    private val numberRegex = Regex("Your\s+number\s+is:\s*([0-9+]+)", RegexOption.IGNORE_CASE)
    private val dataRegex = Regex("Data\s+Internet\s*:?\s*([0-9.]+)", RegexOption.IGNORE_CASE)
    private val validRegex = Regex("Valid\s*:?\s*([0-9.\-/]+)", RegexOption.IGNORE_CASE)
    private val balanceRegex = Regex("Your\s+Balance\s*:?\s*([0-9.]+)", RegexOption.IGNORE_CASE)

    fun parse(text: String): BalanceResult? {
        if (!looksRelevant(text)) return null

        val line = numberRegex.find(text)?.groupValues?.getOrNull(1)
        val dataValues = dataRegex.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
        val balanceValues = balanceRegex.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
        val valid = validRegex.findAll(text).mapNotNull { it.groupValues.getOrNull(1) }.lastOrNull()

        val chosenMb = chooseBestBalance(dataValues, balanceValues)

        return BalanceResult(
            lineNumber = line,
            dataMb = chosenMb,
            validUntil = valid,
            rawMessage = text
        )
    }

    private fun looksRelevant(text: String): Boolean {
        return text.contains("Your number is", true) ||
            text.contains("Data Internet", true) ||
            text.contains("Your Balance", true) ||
            text.contains("Valid", true)
    }

    private fun chooseBestBalance(dataValues: List<Double>, balanceValues: List<Double>): Int? {
        val bestData = dataValues.maxOrNull()
        val bestBalance = balanceValues.maxOrNull()

        return when {
            bestData != null && bestData >= 500 -> bestData.toInt()
            bestBalance != null && bestBalance >= 500 -> bestBalance.toInt()
            bestData != null -> bestData.toInt()
            bestBalance != null -> bestBalance.toInt()
            else -> null
        }
    }
}
