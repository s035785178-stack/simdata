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
import android.view.View
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
import com.stereotip.simdata.util.RequiredApps
import com.stereotip.simdata.util.TelephonyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvLine: TextView
    private lateinit var tvBalanceQuick: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPackageStatus: Button
    private lateinit var btnWarrantyStatus: Button
    private lateinit var logo: ImageView
    private lateinit var btnUpdateTop: ImageView
    private lateinit var accountTopContainer: View
    private lateinit var updateTopContainer: View
    private lateinit var tvValidity: TextView

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
    private var noWarrantyExternal = false

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

        if (!RequiredApps.isRequiredDialerAvailable(this)) {
            startActivity(Intent(this, RequiredAppsActivity::class.java))
            finish()
            return
        }

        tvLine = findViewById(R.id.tvLine)
        tvBalanceQuick = findViewById(R.id.tvBalanceQuick)
        tvUpdated = findViewById(R.id.tvUpdated)
        tvStatus = findViewById(R.id.tvStatus)
        tvValidity = findViewById(R.id.tvValidity)
        btnPackageStatus = findViewById(R.id.btnPackageStatus)
        btnWarrantyStatus = findViewById(R.id.btnWarrantyStatus)
        logo = findViewById(R.id.logo)
        btnUpdateTop = findViewById(R.id.btnUpdateTop)
        accountTopContainer = findViewById(R.id.accountTopContainer)
        updateTopContainer = findViewById(R.id.updateTopContainer)

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
            when {
                noWarrantyExternal -> {
                    Toast.makeText(this, "אין אחריות - נרכש במקום אחר", Toast.LENGTH_SHORT).show()
                }
                warrantyActivated -> {
                    Toast.makeText(this, "תוקף האחריות: $currentWarrantyEnd", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    activateWarranty()
                }
            }
        }

        logo.setOnClickListener { onLogoTapped() }

        btnUpdateTop.setOnClickListener {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        updateTopContainer.setOnClickListener {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        accountTopContainer.setOnClickListener {
            startActivity(Intent(this, CustomerDetailsActivity::class.java))
        }

        showStartupPermissionsDialogIfNeeded()
        triggerSmsPermission()
        updateSummary()
        checkRegistrationIfNeeded()
        loadWarrantyStatus()
        loadPackageStatus()
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
        return AppPrefs.getCustomerName(this).isNotBlank() ||
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

    private fun getCustomerIdentifier(): String {
        val line = normalizeLine(safeDeviceLine())
        if (line.isNotBlank()) return line

        val phone = normalizePhone(AppPrefs.getCustomerPhone(this))
        if (phone.isNotBlank()) return phone

        return ""
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
        tvValidity.text = AppPrefs.getValid(this).orEmpty().ifBlank { "לא ידוע" }
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
                applyCustomerFromDocument(
                    customerName = doc.getString("name").orEmpty().ifBlank { doc.getString("customerName").orEmpty() },
                    customerPhone = normalizePhone(doc.getString("phone").orEmpty().ifBlank { doc.getString("customerPhone").orEmpty() }),
                    carModel = doc.getString("carModel").orEmpty().ifBlank { doc.getString("vehicleModel").orEmpty() },
                    carNumber = doc.getString("carNumber").orEmpty().ifBlank { doc.getString("vehicleNumber").orEmpty() },
                    dataPackage = doc.getString("package").orEmpty().ifBlank { doc.getString("dataPackage").orEmpty() },
                    lineNumber = lineNumber,
                    validUntil = doc.getString("validUntil").orEmpty()
                )
            }
            .addOnFailureListener {
                restoreLookupInProgress = false
                waitingForRestoreAnswer = false
                if (isFinishing || isDestroyed || movedToRegistration) return@addOnFailureListener

                Toast.makeText(this, "ממתין לחיבור כדי לשחזר לקוח קיים", Toast.LENGTH_SHORT).show()
            }
    }


    private fun applyCustomerFromDocument(
        customerName: String,
        customerPhone: String,
        carModel: String,
        carNumber: String,
        dataPackage: String,
        lineNumber: String,
        validUntil: String
    ) {
        if (customerName.isNotBlank()) AppPrefs.setCustomerName(this, customerName)
        if (customerPhone.isNotBlank()) AppPrefs.setCustomerPhone(this, customerPhone)
        if (carModel.isNotBlank()) AppPrefs.setCarModel(this, carModel)
        if (carNumber.isNotBlank()) AppPrefs.setCarNumber(this, carNumber)
        if (dataPackage.isNotBlank()) AppPrefs.setDataPackage(this, dataPackage)
        if (lineNumber.isNotBlank()) AppPrefs.setLineNumber(this, lineNumber)
        if (validUntil.isNotBlank()) AppPrefs.setValid(this, validUntil)

        registrationCheckDone = true
        updateSummary()
        loadWarrantyStatus()
        loadPackageStatus()
    }

    private fun restoreCustomerFromFirebaseByPhone(phone: String) {
        if (restoreLookupInProgress) return

        restoreLookupInProgress = true
        waitingForRestoreAnswer = true

        db.collection("customers")
            .whereEqualTo("phone", phone)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    restoreLookupInProgress = false
                    waitingForRestoreAnswer = false

                    if (isFinishing || isDestroyed || movedToRegistration) return@addOnSuccessListener

                    val doc = result.documents.first()
                    applyCustomerFromDocument(
                        customerName = doc.getString("name").orEmpty().ifBlank { doc.getString("customerName").orEmpty() },
                        customerPhone = normalizePhone(doc.getString("phone").orEmpty().ifBlank { doc.getString("customerPhone").orEmpty() }),
                        carModel = doc.getString("carModel").orEmpty().ifBlank { doc.getString("vehicleModel").orEmpty() },
                        carNumber = doc.getString("carNumber").orEmpty().ifBlank { doc.getString("vehicleNumber").orEmpty() },
                        dataPackage = doc.getString("package").orEmpty().ifBlank { doc.getString("dataPackage").orEmpty() },
                        lineNumber = normalizeLine(doc.getString("lineNumber")).ifBlank { phone },
                        validUntil = doc.getString("validUntil").orEmpty()
                    )
                } else {
                    db.collection("customers")
                        .whereEqualTo("customerPhone", phone)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { legacy ->
                            restoreLookupInProgress = false
                            waitingForRestoreAnswer = false

                            if (isFinishing || isDestroyed || movedToRegistration) return@addOnSuccessListener

                            if (legacy.isEmpty) {
                                moveToRegistration(clearLocal = true)
                                return@addOnSuccessListener
                            }

                            val doc = legacy.documents.first()
                            applyCustomerFromDocument(
                                customerName = doc.getString("name").orEmpty().ifBlank { doc.getString("customerName").orEmpty() },
                                customerPhone = normalizePhone(doc.getString("phone").orEmpty().ifBlank { doc.getString("customerPhone").orEmpty() }),
                                carModel = doc.getString("carModel").orEmpty().ifBlank { doc.getString("vehicleModel").orEmpty() },
                                carNumber = doc.getString("carNumber").orEmpty().ifBlank { doc.getString("vehicleNumber").orEmpty() },
                                dataPackage = doc.getString("package").orEmpty().ifBlank { doc.getString("dataPackage").orEmpty() },
                                lineNumber = normalizeLine(doc.getString("lineNumber")).ifBlank { phone },
                                validUntil = doc.getString("validUntil").orEmpty()
                            )
                        }
                        .addOnFailureListener {
                            restoreLookupInProgress = false
                            waitingForRestoreAnswer = false
                            if (isFinishing || isDestroyed || movedToRegistration) return@addOnFailureListener
                            Toast.makeText(this, "ממתין לחיבור כדי לשחזר לקוח קיים", Toast.LENGTH_SHORT).show()
                        }
                }
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

        val savedPhone = normalizePhone(AppPrefs.getCustomerPhone(this))
        val normalizedLine = normalizeLine(safeDeviceLine())

        if (normalizedLine.isNotBlank()) {
            AppPrefs.setLineNumber(this, normalizedLine)
            restoreCustomerFromFirebaseByLine(normalizedLine)
            return
        }

        if (savedPhone.isNotBlank()) {
            restoreCustomerFromFirebaseByPhone(savedPhone)
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
                val pkg = doc.getString("package").orEmpty().ifBlank { doc.getString("dataPackage").orEmpty() }

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
        val lineIdentifier = normalizeLine(safeDeviceLine()).ifBlank { normalizeLine(AppPrefs.getLineNumber(this)) }
        val phoneIdentifier = normalizePhone(AppPrefs.getCustomerPhone(this))

        fun bindDefault() {
            warrantyActivated = false
            currentWarrantyEnd = ""
            noWarrantyExternal = false
            btnWarrantyStatus.text = "הפעל תקופת אחריות✔️"
            btnWarrantyStatus.isEnabled = true
            AppPrefs.setWarrantyEnd(this, "")
            AppPrefs.setWarrantyActive(this, false)
        }

        fun applyFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot) {
            val warrantyEnd = doc.getString("warrantyEnd").orEmpty()
            val warrantyStart = doc.get("warrantyStart")
            val noWarrantyFlag = doc.getBoolean("noWarrantyExternal") == true
            noWarrantyExternal = noWarrantyFlag
            if (noWarrantyExternal) {
                warrantyActivated = false
                currentWarrantyEnd = ""
                btnWarrantyStatus.text = "אין אחריות (נרכש במקום אחר)"
                btnWarrantyStatus.isEnabled = false
                AppPrefs.setWarrantyEnd(this, "")
                AppPrefs.setWarrantyActive(this, false)
                return
            }
            btnWarrantyStatus.isEnabled = true
            val hasStart = when (warrantyStart) {
                is Number -> warrantyStart.toLong() > 0L
                is String -> warrantyStart.isNotBlank()
                else -> false
            }
            if (warrantyEnd.isBlank() || !hasStart) {
                bindDefault()
            } else {
                warrantyActivated = true
                currentWarrantyEnd = warrantyEnd
                btnWarrantyStatus.text = "תוקף אחריות $warrantyEnd🛡️"
                AppPrefs.setWarrantyEnd(this, warrantyEnd)
                AppPrefs.setWarrantyActive(this, true)
            }
        }

        if (lineIdentifier.isBlank() && phoneIdentifier.isBlank()) {
            bindDefault()
            return
        }

        val handlePhoneFallback: () -> Unit = {
            if (phoneIdentifier.isBlank()) {
                bindDefault()
            } else {
                db.collection("customers").whereEqualTo("phone", phoneIdentifier).limit(1).get()
                    .addOnSuccessListener { res ->
                        if (!res.isEmpty) applyFromDoc(res.documents.first())
                        else db.collection("customers").whereEqualTo("customerPhone", phoneIdentifier).limit(1).get()
                            .addOnSuccessListener { legacy ->
                                if (!legacy.isEmpty) applyFromDoc(legacy.documents.first()) else bindDefault()
                            }
                            .addOnFailureListener { if (!isFinishing && !isDestroyed) bindDefault() }
                    }
                    .addOnFailureListener { if (!isFinishing && !isDestroyed) bindDefault() }
            }
        }

        if (lineIdentifier.isNotBlank()) {
            db.collection("customers").whereEqualTo("lineNumber", lineIdentifier).limit(1).get()
                .addOnSuccessListener { result ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener
                    if (!result.isEmpty) applyFromDoc(result.documents.first()) else handlePhoneFallback()
                }
                .addOnFailureListener { if (!isFinishing && !isDestroyed) handlePhoneFallback() }
        } else {
            handlePhoneFallback()
        }
    }


    private fun findCustomerDocument(
        onFound: (com.google.firebase.firestore.DocumentSnapshot) -> Unit,
        onMissing: () -> Unit,
        onError: () -> Unit
    ) {
        val lineIdentifier = normalizeLine(safeDeviceLine()).ifBlank { normalizeLine(AppPrefs.getLineNumber(this)) }
        val phoneIdentifier = normalizePhone(AppPrefs.getCustomerPhone(this))

        fun findByPhone() {
            if (phoneIdentifier.isBlank()) {
                onMissing()
                return
            }

            db.collection("customers").document(phoneIdentifier).get()
                .addOnSuccessListener { direct ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener
                    if (direct.exists()) {
                        onFound(direct)
                        return@addOnSuccessListener
                    }

                    db.collection("customers").whereEqualTo("phone", phoneIdentifier).limit(1).get()
                        .addOnSuccessListener { res ->
                            if (isFinishing || isDestroyed) return@addOnSuccessListener
                            if (!res.isEmpty) {
                                onFound(res.documents.first())
                            } else {
                                db.collection("customers").whereEqualTo("customerPhone", phoneIdentifier).limit(1).get()
                                    .addOnSuccessListener { legacy ->
                                        if (isFinishing || isDestroyed) return@addOnSuccessListener
                                        if (!legacy.isEmpty) onFound(legacy.documents.first()) else onMissing()
                                    }
                                    .addOnFailureListener { if (!isFinishing && !isDestroyed) onError() }
                            }
                        }
                        .addOnFailureListener { if (!isFinishing && !isDestroyed) onError() }
                }
                .addOnFailureListener { if (!isFinishing && !isDestroyed) onError() }
        }

        if (lineIdentifier.isBlank()) {
            findByPhone()
            return
        }

        db.collection("customers").whereEqualTo("lineNumber", lineIdentifier).limit(1).get()
            .addOnSuccessListener { result ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (!result.isEmpty) {
                    onFound(result.documents.first())
                } else {
                    findByPhone()
                }
            }
            .addOnFailureListener { if (!isFinishing && !isDestroyed) findByPhone() }
    }


    private fun activateWarranty() {
        findCustomerDocument(
            onFound = { doc ->
                val noWarrantyFlag = doc.getBoolean("noWarrantyExternal") == true

                if (noWarrantyFlag) {
                    noWarrantyExternal = true
                    btnWarrantyStatus.text = "אין אחריות (נרכש במקום אחר)"
                    btnWarrantyStatus.isEnabled = false
                    Toast.makeText(this, "הלקוח מסומן ללא אחריות", Toast.LENGTH_SHORT).show()
                    return@findCustomerDocument
                }

                val existingWarrantyStart = doc.getLong("warrantyStart")
                val existingWarrantyEnd = doc.getString("warrantyEnd").orEmpty()

                if (existingWarrantyStart != null && existingWarrantyEnd.isNotBlank()) {
                    Toast.makeText(
                        this,
                        "האחריות כבר הופעלה במכשיר זה. לשינויים יש לפנות לסטריאו טיפ אביזרי רכב",
                        Toast.LENGTH_LONG
                    ).show()
                    loadWarrantyStatus()
                    return@findCustomerDocument
                }

                val startMillis = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                cal.timeInMillis = startMillis
                cal.add(Calendar.YEAR, 1)
                val endDate = dateFormat.format(cal.time)

                val data = linkedMapOf<String, Any?>(
                    "warrantyStart" to startMillis,
                    "warrantyEnd" to endDate,
                    "warrantyActive" to true,
                    "lastUpdate" to System.currentTimeMillis(),
                    "noWarrantyExternal" to false
                )

                doc.reference.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "האחריות הופעלה בהצלחה", Toast.LENGTH_SHORT).show()
                        loadWarrantyStatus()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "שגיאה בהפעלת אחריות", Toast.LENGTH_SHORT).show()
                    }
            },
            onMissing = {
                Toast.makeText(this, "לא נמצא לקוח במערכת. בדוק שהלקוח נטען מחדש מהשרת.", Toast.LENGTH_SHORT).show()
            },
            onError = {
                Toast.makeText(this, "שגיאה באיתור לקוח", Toast.LENGTH_SHORT).show()
            }
        )
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
        AppPrefs.clearCustomerProfile(this)
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
        return when (normalized) {
            "לא זוהה", "לא זוהה מספר", "לא אושרו הרשאות" -> ""
            else -> normalized
        }
    }
}
