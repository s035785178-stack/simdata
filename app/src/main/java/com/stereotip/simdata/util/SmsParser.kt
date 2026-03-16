package com.stereotip.simdata.util

data class BalanceResult(
    val balanceText: String = "",
    val validText: String = "",
    val source: String = ""
)

object SmsParser {

    private val dataInternetRegex =
        Regex("""Data\s*Internet\s*:?\s*([0-9.]+)""", RegexOption.IGNORE_CASE)

    private val yourBalanceRegex =
        Regex("""Your\s*Balance\s*:?\s*([0-9.]+)""", RegexOption.IGNORE_CASE)

    private val validRegex =
        Regex("""Valid\s*:?\s*([0-9.\-\/]+)""", RegexOption.IGNORE_CASE)

    fun parse(message: String): BalanceResult {
        val dataInternet = dataInternetRegex.find(message)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val yourBalance = yourBalanceRegex.find(message)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val valid = validRegex.find(message)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val chosen = chooseBestBalance(dataInternet, yourBalance)

        return BalanceResult(
            balanceText = chosen.first,
            validText = valid,
            source = chosen.second
        )
    }

    private fun chooseBestBalance(dataInternet: String, yourBalance: String): Pair<String, String> {
        val dataNum = dataInternet.toDoubleOrNull()
        val balanceNum = yourBalance.toDoubleOrNull()

        return when {
            dataNum != null && dataNum >= 500 -> Pair(formatMb(dataInternet), "Data Internet")
            balanceNum != null && balanceNum >= 500 -> Pair(formatMb(yourBalance), "Your Balance")
            dataInternet.isNotBlank() -> Pair(formatMb(dataInternet), "Data Internet")
            yourBalance.isNotBlank() -> Pair(formatMb(yourBalance), "Your Balance")
            else -> Pair("", "")
        }
    }

    private fun formatMb(value: String): String {
        return if (value.isBlank()) "" else "${value} MB"
    }
}
