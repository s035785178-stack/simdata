package com.stereotip.simdata.data

data class BalanceResult(
    val lineNumber: String? = null,
    val dataMb: Int? = null,
    val validUntil: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val rawMessage: String? = null
)

data class PackageOption(
    val title: String,
    val subtitle: String,
    val price: String,
    val messageValue: String,
    val featured: Boolean = false
)

data class NetworkCheckResult(
    val simStatus: String,
    val networkType: String,
    val internetStatus: String,
    val roamingStatus: String,
    val apnStatus: String,
    val mobileDataStatus: String,
    val lineNumber: String,
    val updatedAt: Long = System.currentTimeMillis()
)
