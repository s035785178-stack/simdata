package com.stereotip.simdata

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stereotip.simdata.receiver.SmsReceiver
import com.stereotip.simdata.util.AppPrefs
import com.stereotip.simdata.util.Formatter
import com.stereotip.simdata.util.PhoneUtils
import com.stereotip.simdata.util.TelephonyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvLine: TextView
    private lateinit var tvBalanceQuick: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPackageStatus: Button
    private lateinit var btnWarrantyStatus: Button
    private lateinit var logo: ImageView
    private lateinit var btnUpdateTop: ImageView

    private var updateCheckInProgress = false
    private var updateDialogShowing = false

    private var logoTapCount = 0
    private var lastTapTime = 0L
    private var registrationCheckDone = false
    private var movedToRegistration = false
    private var balanceReceiverRegistered = false
    private var startupDialogShown = false
    private var permissionRequestInProgress = false
    private var restoreLookupInProgress = false
    private var waitingForRestoreAnswer = false

    private var warrantyActivated = false
    private var currentWarrantyEnd = ""

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

    private fun phonePermissions() = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS
    )

    private fun smsPermissions() = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            permissionRequestInProgress = false
            startupDialogShown = false
            continuePermissionFlowIfNeeded()
            updateSummary()
            checkRegistrationIfNeeded()
            loadWarrantyStatus()
            loadPackageStatus()
        }

    private val balanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isFinishing || isDestroyed) return
            updateSummary()
            loadWarrantyStatus()
            loadPackageStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppPrefs.ensureInstallTimestamp(this)

        tvLine = findViewById(R.id.tvLine)
        tvBalanceQuick = findViewById(R.id.tvBalanceQuick)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvStatus = findViewById(R.id.tvStatus)
        btnPackageStatus = findViewById(R.id.btnPackageStatus)
        btnWarrantyStatus = findViewById(R.id.btnWarrantyStatus)
        logo = findViewById(R.id.logo)
        btnUpdateTop = findViewById(R.id.btnUpdateTop)

        findViewById<Button>(R.id.btnBalance).setOnClickListener {
            startActivity(Intent(this, BalanceActivity::class.java))
        }

        findViewById<Button>(R.id.btnNetwork).setOnClickListener {
            startActivity(Intent(this, NetworkCheckActivity::class.java))
        }

        findViewById<Button>(R.id.btnPackages).setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
        }

        findViewById<Button>(R.id.btnSupport).setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        btnWarrantyStatus.setOnClickListener {
            if (warrantyActivated) {
                Toast.makeText(this, "תוקף האחריות: $currentWarrantyEnd", Toast.LENGTH_SHORT).show()
            } else {
                activateWarranty()
            }
        }

        logo.setOnClickListener { onLogoTapped() }

        btnUpdateTop.setOnClickListener {
            openUpdateScreen(forceMode = false)
        }

        showStartupPermissionsDialogIfNeeded()
        triggerSmsPermission()
        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
        loadPackageStatus()
        checkForAppUpdates(showAlreadyUpdatedToast = false)
    }

    override fun onResume() {
        super.onResume()

        continuePermissionFlowIfNeeded()

        if (!balanceReceiverRegistered) {
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(balanceReceiver, IntentFilter(SmsReceiver.ACTION_BALANCE_UPDATED))
            balanceReceiverRegistered = true
        }

        if (movedToRegistration) return

        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
        loadPackageStatus()
        checkForAppUpdates(showAlreadyUpdatedToast = false)
    }

    override fun onPause() {
        if (balanceReceiverRegistered) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(balanceReceiver)
            } catch (_: Exception) {
            }
            balanceReceiverRegistered = false
        }
        super.onPause()
    }

    private fun hasSavedRegisteredUser(): Boolean {
        return AppPrefs.getCustomerName(this).isNotBlank() &&
            AppPrefs.getCustomerPhone(this).isNotBlank()
    }

    private fun showStartupPermissionsDialogIfNeeded() {
        if (!hasAnyMissingStartupPermission()) return
        if (startupDialogShown) return
        if (permissionRequestInProgress) return

        startupDialogShown = true

        AlertDialog.Builder(this)
            .setTitle("נדרשות הרשאות להפעלת האפליקציה")
            .setMessage("האפליקציה צריכה הרשאות טלפון והודעות SMS כדי לזהות מספר קו ולעבוד תקין.")
            .setPositiveButton("אשר הרשאות") { _, _ ->
                continuePermissionFlowIfNeeded()
            }
            .setCancelable(false)
            .show()
    }

    private fun continuePermissionFlowIfNeeded() {
        if (permissionRequestInProgress) return

        val missingPhone = getEffectiveMissingPhonePermissions()
        if (missingPhone.isNotEmpty()) {
            permissionRequestInProgress = true
            permissionLauncher.launch(missingPhone.toTypedArray())
            return
        }

        val missingSms = getEffectiveMissingSmsPermissions()
        if (missingSms.isNotEmpty()) {
            permissionRequestInProgress = true
            permissionLauncher.launch(missingSms.toTypedArray())
        }
    }

    private fun getMissingPermissions(group: Array<String>): List<String> {
        return group.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasRealSmsAccess(): Boolean {
        return try {
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                null
            )
            cursor?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readLineDirect(): String {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val raw = tm.line1Number.orEmpty()
            normalizeLine(PhoneUtils.normalizeToLocal(raw))
        } catch (_: SecurityException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun hasRealPhoneAccess(): Boolean {
        val saved = normalizeLine(AppPrefs.getLineNumber(this))
        if (saved.isNotBlank()) return true

        val direct = readLineDirect()
        if (direct.isNotBlank()) return true

        return try {
            normalizeLine(TelephonyUtils.getLineNumber(this)).isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun getEffectiveMissingPhonePermissions(): List<String> {
        val missing = getMissingPermissions(phonePermissions())
        return if (missing.isNotEmpty() && (hasRealPhoneAccess() || hasSavedRegisteredUser())) {
            emptyList()
        } else {
            missing
        }
    }

    private fun getEffectiveMissingSmsPermissions(): List<String> {
        val missing = getMissingPermissions(smsPermissions())
        return if (missing.isNotEmpty() && (hasRealSmsAccess() || hasSavedRegisteredUser())) {
            emptyList()
        } else {
            missing
        }
    }

    private fun hasAnyMissingStartupPermission(): Boolean {
        return getEffectiveMissingPhonePermissions().isNotEmpty() ||
            getEffectiveMissingSmsPermissions().isNotEmpty()
    }

    private fun triggerSmsPermission() {
        try {
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                null
            )
            cursor?.close()
        } catch (_: Exception) {
        }
    }

    private fun safeDeviceLine(): String {
        val saved = normalizeLine(AppPrefs.getLineNumber(this))
        if (saved.isNotBlank()) return saved

        val direct = readLineDirect()
        if (direct.isNotBlank()) return direct

        return try {
            normalizeLine(TelephonyUtils.getLineNumber(this))
        } catch (_: Exception) {
            ""
        }
    }

    private fun updateSummary() {
        val line = safeDeviceLine()
        tvLine.text = if (line.isBlank()) "לא זוהה מספר" else line

        if (line.isNotBlank()) {
            AppPrefs.setLineNumber(this, line)
        }

        val mb = AppPrefs.getBalanceMb(this)
        tvBalanceQuick.text = mb?.let { Formatter.mbToDisplay(it) } ?: "לא בוצעה בדיקה"
        tvUpdated.text = Formatter.formatDate(AppPrefs.getUpdated(this))
        tvStatus.text = Formatter.balanceStatus(mb)
    }

    private fun restoreCustomerFromFirebaseByLine(lineNumber: String) {
        if (restoreLookupInProgress) return

        restoreLookupInProgress = true
        waitingForRestoreAnswer = true

        db.collection("customers")
            .whereEqualTo("lineNumber", lineNumber)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                restoreLookupInProgress = false
                waitingForRestoreAnswer = false

                if (isFinishing || isDestroyed || movedToRegistration) return@addOnSuccessListener

                if (result.isEmpty) {
                    moveToRegistration(clearLocal = false)
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val customerName = doc.getString("customerName").orEmpty()
                val customerPhone = normalizePhone(doc.getString("customerPhone"))
                val carModel = doc.getString("carModel").orEmpty()
                val carNumber = doc.getString("carNumber").orEmpty()
                val dataPackage = doc.getString("dataPackage").orEmpty()

                if (customerName.isNotBlank()) AppPrefs.setCustomerName(this, customerName)
                if (customerPhone.isNotBlank()) AppPrefs.setCustomerPhone(this, customerPhone)
                if (carModel.isNotBlank()) AppPrefs.setCarModel(this, carModel)
                if (carNumber.isNotBlank()) AppPrefs.setCarNumber(this, carNumber)
                if (dataPackage.isNotBlank()) AppPrefs.setDataPackage(this, dataPackage)
                AppPrefs.setLineNumber(this, lineNumber)

                registrationCheckDone = true
                updateSummary()
                loadWarrantyStatus()
                loadPackageStatus()
            }
            .addOnFailureListener {
                restoreLookupInProgress = false
                waitingForRestoreAnswer = false
                if (isFinishing || isDestroyed || movedToRegistration) return@addOnFailureListener

                Toast.makeText(this, "ממתין לחיבור כדי לשחזר לקוח קיים", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkRegistrationIfNeeded() {
        if (registrationCheckDone || movedToRegistration || restoreLookupInProgress || waitingForRestoreAnswer) {
            return
        }

        val savedPhone = AppPrefs.getCustomerPhone(this)
        val savedName = AppPrefs.getCustomerName(this)

        if (savedPhone.isNotBlank() && savedName.isNotBlank()) {
            registrationCheckDone = true
            return
        }

        val normalizedLine = normalizeLine(safeDeviceLine())

        if (normalizedLine.isNotBlank()) {
            AppPrefs.setLineNumber(this, normalizedLine)
            restoreCustomerFromFirebaseByLine(normalizedLine)
            return
        }

        moveToRegistration(clearLocal = false)
    }

    private fun moveToRegistration(clearLocal: Boolean) {
        if (movedToRegistration) return

        movedToRegistration = true

        if (clearLocal) {
            clearLocalCustomer()
        }

        val intent = Intent(this, RegistrationActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun loadPackageStatus() {
        val savedPackage = AppPrefs.getDataPackage(this)
        val savedLine = normalizeLine(AppPrefs.getLineNumber(this))
        val deviceLine = normalizeLine(safeDeviceLine())

        val normalizedLine = when {
            savedLine.isNotBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> ""
        }

        if (normalizedLine.isBlank()) {
            btnPackageStatus.text = "📦 סוג חבילה\n${savedPackage.ifBlank { "לא ידוע" }}"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (result.isEmpty) {
                    btnPackageStatus.text = "📦 סוג חבילה\n${savedPackage.ifBlank { "לא ידוע" }}"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val pkg = doc.getString("dataPackage").orEmpty()

                if (pkg.isNotBlank()) {
                    AppPrefs.setDataPackage(this, pkg)
                }

                btnPackageStatus.text = "📦 סוג חבילה\n${pkg.ifBlank { savedPackage.ifBlank { "לא ידוע" } }}"
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    btnPackageStatus.text = "📦 סוג חבילה\n${savedPackage.ifBlank { "שגיאה" }}"
                }
            }
    }

    private fun loadWarrantyStatus() {
        val savedLine = normalizeLine(AppPrefs.getLineNumber(this))
        val deviceLine = normalizeLine(safeDeviceLine())

        val normalizedLine = when {
            savedLine.isNotBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> ""
        }

        if (normalizedLine.isBlank()) {
            warrantyActivated = false
            currentWarrantyEnd = ""
            btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (result.isEmpty) {
                    warrantyActivated = false
                    currentWarrantyEnd = ""
                    btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val warrantyEnd = doc.getString("warrantyEnd").orEmpty()
                val warrantyStart = doc.getLong("warrantyStart")

                if (warrantyEnd.isBlank() || warrantyStart == null || warrantyStart <= 0L) {
                    warrantyActivated = false
                    currentWarrantyEnd = ""
                    btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
                } else {
                    warrantyActivated = true
                    currentWarrantyEnd = warrantyEnd
                    btnWarrantyStatus.text = "תוקף אחריות $warrantyEnd🛡️"
                }
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    warrantyActivated = false
                    currentWarrantyEnd = ""
                    btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
                }
            }
    }

    private fun activateWarranty() {
        val savedLine = normalizeLine(AppPrefs.getLineNumber(this))
        val deviceLine = normalizeLine(safeDeviceLine())

        val normalizedLine = when {
            savedLine.isNotBlank() -> savedLine
            deviceLine.isNotBlank() -> deviceLine
            else -> ""
        }

        if (normalizedLine.isBlank()) {
            Toast.makeText(this, "לא זוהה מספר קו", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("customers")
            .whereEqualTo("lineNumber", normalizedLine)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "לא נמצא לקוח במערכת", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = result.documents.first()
                val existingWarrantyStart = doc.getLong("warrantyStart")
                val existingWarrantyEnd = doc.getString("warrantyEnd").orEmpty()

                if (existingWarrantyStart != null && existingWarrantyEnd.isNotBlank()) {
                    Toast.makeText(
                        this,
                        "האחריות כבר הופעלה במכשיר זה. לשינויים יש לפנות לסטריאו טיפ אביזרי רכב",
                        Toast.LENGTH_LONG
                    ).show()
                    loadWarrantyStatus()
                    return@addOnSuccessListener
                }

                val startMillis = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                cal.timeInMillis = startMillis
                cal.add(Calendar.YEAR, 1)
                val endDate = dateFormat.format(cal.time)

                val data: HashMap<String, Any?> = hashMapOf(
                    "warrantyStart" to startMillis,
                    "warrantyEnd" to endDate,
                    "lastUpdate" to System.currentTimeMillis()
                )

                doc.reference.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "האחריות הופעלה בהצלחה", Toast.LENGTH_SHORT).show()
                        loadWarrantyStatus()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "שגיאה בהפעלת אחריות", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה באיתור לקוח", Toast.LENGTH_SHORT).show()
            }
    }


    private fun checkForAppUpdates(showAlreadyUpdatedToast: Boolean) {
        if (updateCheckInProgress) return

        updateCheckInProgress = true
        thread {
            val result = UpdateManager.fetchUpdateInfo()

            runOnUiThread {
                updateCheckInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread

                result.onSuccess { info ->
                    handleUpdateInfo(info, showAlreadyUpdatedToast)
                }.onFailure {
                    if (showAlreadyUpdatedToast) {
                        Toast.makeText(this, "לא הצלחנו לבדוק עדכונים כרגע", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleUpdateInfo(info: UpdateInfo, showAlreadyUpdatedToast: Boolean) {
        val currentVersionCode = packageVersionCode()

        if (info.versionCode <= currentVersionCode) {
            AppPrefs.clearDismissedRecommendedUpdateVersion(this)
            if (showAlreadyUpdatedToast) {
                Toast.makeText(this, "האפליקציה כבר מעודכנת", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (info.forceUpdate) {
            showForceUpdateDialog(info)
            return
        }

        val dismissedVersion = AppPrefs.getDismissedRecommendedUpdateVersion(this)
        if (dismissedVersion == info.versionCode) {
            return
        }

        showRecommendedUpdateDialog(info)
    }

    private fun showForceUpdateDialog(info: UpdateInfo) {
        if (updateDialogShowing) return
        updateDialogShowing = true

        AlertDialog.Builder(this)
            .setTitle(info.title)
            .setMessage(info.message)
            .setCancelable(false)
            .setPositiveButton("עדכן עכשיו") { _, _ ->
                updateDialogShowing = false
                openUpdateScreen(forceMode = true)
            }
            .setNegativeButton("סגור אפליקציה") { _, _ ->
                updateDialogShowing = false
                finishAffinity()
            }
            .setOnDismissListener {
                updateDialogShowing = false
            }
            .show()
    }

    private fun showRecommendedUpdateDialog(info: UpdateInfo) {
        if (updateDialogShowing) return
        updateDialogShowing = true

        AlertDialog.Builder(this)
            .setTitle(info.title)
            .setMessage(info.message)
            .setCancelable(true)
            .setPositiveButton("עדכן עכשיו") { _, _ ->
                updateDialogShowing = false
                openUpdateScreen(forceMode = false)
            }
            .setNegativeButton("אחר כך") { _, _ ->
                updateDialogShowing = false
                AppPrefs.setDismissedRecommendedUpdateVersion(this, info.versionCode)
            }
            .setOnDismissListener {
                updateDialogShowing = false
            }
            .show()
    }

    private fun openUpdateScreen(forceMode: Boolean) {
        val intent = Intent(this, UpdateActivity::class.java)
        intent.putExtra(UpdateActivity.EXTRA_FORCE_MODE, forceMode)
        startActivity(intent)
    }

    private fun packageVersionCode(): Int {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
    }

    private fun onLogoTapped() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 4000) logoTapCount = 0
        lastTapTime = now
        logoTapCount++
        if (logoTapCount >= 7) {
            logoTapCount = 0
            startActivity(Intent(this, TechnicianActivity::class.java))
        }
    }

    private fun clearLocalCustomer() {
        AppPrefs.setCustomerName(this, "")
        AppPrefs.setCustomerPhone(this, "")
        AppPrefs.setCarModel(this, "")
        AppPrefs.setCarNumber(this, "")
        AppPrefs.setDataPackage(this, "לא ידוע / אין")
    }

    private fun normalizeLine(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }

    private fun normalizePhone(raw: String?): String {
        val normalized = PhoneUtils.normalizeToLocal(raw)
        return if (normalized == "לא זוהה") "" else normalized
    }
}
