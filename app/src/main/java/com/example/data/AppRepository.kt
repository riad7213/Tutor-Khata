package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.BackupSnapshot
import com.example.model.ClassLog
import com.example.model.Payment
import com.example.model.Settings
import com.example.model.Student
import com.example.model.StudentBalanceInfo
import com.example.utils.DateUtils
import com.example.utils.LedgerEngine
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

enum class SyncStatus {
    SYNCED, SYNCING, OFFLINE
}

class AppRepository(
    private val context: Context,
    private val localRepo: LocalRepository,
    private val firestoreRepo: FirestoreRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    private val _classLogs = MutableStateFlow<List<ClassLog>>(emptyList())
    val classLogs: StateFlow<List<ClassLog>> = _classLogs.asStateFlow()

    private val _payments = MutableStateFlow<List<Payment>>(emptyList())
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _studentBalances = MutableStateFlow<Map<String, StudentBalanceInfo>>(emptyMap())
    val studentBalances: StateFlow<Map<String, StudentBalanceInfo>> = _studentBalances.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus.OFFLINE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private var remoteListenersJob: Job? = null

    init {
        loadFromLocal()
        autoPurgeTrashOlderThan30Days()
        recomputeLedger()
        checkAndCreateDailySnapshot()
    }

    private fun loadFromLocal() {
        val s = localRepo.loadStudents()
        val logs = localRepo.loadClassLogs()
        val p = localRepo.loadPayments()
        val set = localRepo.loadSettings()

        _students.value = s
        _classLogs.value = logs
        _payments.value = p
        _settings.value = set
    }

    fun onUserChanged(user: FirebaseUser?) {
        _currentUser.value = user
        remoteListenersJob?.cancel()

        if (user == null) {
            _syncStatus.value = SyncStatus.OFFLINE
            loadFromLocal()
            recomputeLedger()
        } else {
            _syncStatus.value = SyncStatus.SYNCING
            remoteListenersJob = scope.launch {
                launch {
                    firestoreRepo.observeStudents(user.uid).collect { remoteList ->
                        if (remoteList.isNotEmpty() || _students.value.isEmpty()) {
                            _students.value = remoteList
                            localRepo.saveStudents(remoteList)
                            recomputeLedger()
                        }
                        _syncStatus.value = SyncStatus.SYNCED
                    }
                }
                launch {
                    firestoreRepo.observeClassLogs(user.uid).collect { remoteList ->
                        if (remoteList.isNotEmpty() || _classLogs.value.isEmpty()) {
                            _classLogs.value = remoteList
                            localRepo.saveClassLogs(remoteList)
                            recomputeLedger()
                        }
                        _syncStatus.value = SyncStatus.SYNCED
                    }
                }
                launch {
                    firestoreRepo.observePayments(user.uid).collect { remoteList ->
                        if (remoteList.isNotEmpty() || _payments.value.isEmpty()) {
                            _payments.value = remoteList
                            localRepo.savePayments(remoteList)
                            recomputeLedger()
                        }
                        _syncStatus.value = SyncStatus.SYNCED
                    }
                }
                launch {
                    firestoreRepo.observeSettings(user.uid).collect { remoteSettings ->
                        _settings.value = remoteSettings
                        localRepo.saveSettings(remoteSettings)
                        recomputeLedger()
                    }
                }
            }
        }
    }

    fun recomputeLedger() {
        _studentBalances.value = LedgerEngine.computeAllBalances(
            students = _students.value,
            classLogs = _classLogs.value,
            payments = _payments.value
        )
    }

    private fun autoPurgeTrashOlderThan30Days() {
        val today = Calendar.getInstance()
        val cutoffMillis = today.timeInMillis - (30L * 24 * 60 * 60 * 1000)

        fun isOlderThan30Days(deletedAt: String?): Boolean {
            if (deletedAt == null) return false
            val cal = DateUtils.parseIsoDate(deletedAt)
            return cal.timeInMillis < cutoffMillis
        }

        val keptStudents = _students.value.filterNot { it.deletedAt != null && isOlderThan30Days(it.deletedAt) }
        val keptLogs = _classLogs.value.filterNot { it.deletedAt != null && isOlderThan30Days(it.deletedAt) }
        val keptPayments = _payments.value.filterNot { it.deletedAt != null && isOlderThan30Days(it.deletedAt) }

        if (keptStudents.size != _students.value.size ||
            keptLogs.size != _classLogs.value.size ||
            keptPayments.size != _payments.value.size
        ) {
            _students.value = keptStudents
            _classLogs.value = keptLogs
            _payments.value = keptPayments
            localRepo.saveStudents(keptStudents)
            localRepo.saveClassLogs(keptLogs)
            localRepo.savePayments(keptPayments)
        }
    }

    // --- STUDENT OPERATIONS ---
    fun saveStudent(student: Student) {
        val now = DateUtils.todayIso()
        val s = student.copy(
            updatedAt = now,
            createdAt = student.createdAt.ifEmpty { now }
        )
        val current = _students.value.toMutableList()
        val idx = current.indexOfFirst { it.id == s.id }
        if (idx >= 0) {
            current[idx] = s
        } else {
            current.add(s)
        }
        _students.value = current
        localRepo.saveStudents(current)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    _syncStatus.value = SyncStatus.SYNCING
                    firestoreRepo.saveStudent(user.uid, s)
                    _syncStatus.value = SyncStatus.SYNCED
                } catch (e: Exception) {
                    Log.e("AppRepo", "Sync student failed", e)
                    _syncStatus.value = SyncStatus.OFFLINE
                }
            }
        }
    }

    fun softDeleteStudent(studentId: String) {
        val now = DateUtils.todayIso()
        val s = _students.value.find { it.id == studentId } ?: return
        saveStudent(s.copy(deletedAt = now))
    }

    fun restoreStudent(studentId: String) {
        val now = DateUtils.todayIso()
        val s = _students.value.find { it.id == studentId } ?: return
        saveStudent(s.copy(deletedAt = null, updatedAt = now))
    }

    fun deleteStudentPermanently(studentId: String) {
        val current = _students.value.filter { it.id != studentId }
        _students.value = current
        localRepo.saveStudents(current)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    firestoreRepo.deleteStudentPermanently(user.uid, studentId)
                } catch (e: Exception) {
                    Log.e("AppRepo", "Delete student perm failed", e)
                }
            }
        }
    }

    // --- CLASS LOG OPERATIONS ---
    fun saveClassLog(classLog: ClassLog) {
        val now = DateUtils.todayIso()
        val log = classLog.copy(
            updatedAt = now,
            createdAt = classLog.createdAt.ifEmpty { now }
        )
        val current = _classLogs.value.toMutableList()
        val idx = current.indexOfFirst { it.id == log.id }
        if (idx >= 0) {
            current[idx] = log
        } else {
            current.add(log)
        }
        _classLogs.value = current
        localRepo.saveClassLogs(current)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    _syncStatus.value = SyncStatus.SYNCING
                    firestoreRepo.saveClassLog(user.uid, log)
                    _syncStatus.value = SyncStatus.SYNCED
                } catch (e: Exception) {
                    Log.e("AppRepo", "Sync classLog failed", e)
                    _syncStatus.value = SyncStatus.OFFLINE
                }
            }
        }
    }

    fun softDeleteClassLog(logId: String) {
        val now = DateUtils.todayIso()
        val log = _classLogs.value.find { it.id == logId } ?: return
        saveClassLog(log.copy(deletedAt = now))
    }

    fun restoreClassLog(logId: String) {
        val now = DateUtils.todayIso()
        val log = _classLogs.value.find { it.id == logId } ?: return
        saveClassLog(log.copy(deletedAt = null, updatedAt = now))
    }

    fun deleteClassLogPermanently(logId: String) {
        val current = _classLogs.value.filter { it.id != logId }
        _classLogs.value = current
        localRepo.saveClassLogs(current)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    firestoreRepo.deleteClassLogPermanently(user.uid, logId)
                } catch (_: Exception) {}
            }
        }
    }

    // --- PAYMENT OPERATIONS ---
    fun savePayment(payment: Payment) {
        val now = DateUtils.todayIso()
        val p = payment.copy(
            updatedAt = now,
            createdAt = payment.createdAt.ifEmpty { now }
        )
        val current = _payments.value.toMutableList()
        val idx = current.indexOfFirst { it.id == p.id }
        if (idx >= 0) {
            current[idx] = p
        } else {
            current.add(p)
        }
        _payments.value = current
        localRepo.savePayments(current)

        // Remember last used payment method in settings
        if (p.method.isNotBlank()) {
            val updatedSettings = _settings.value.copy(lastUsedPaymentMethod = p.method)
            updateSettings(updatedSettings)
        }

        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    _syncStatus.value = SyncStatus.SYNCING
                    firestoreRepo.savePayment(user.uid, p)
                    _syncStatus.value = SyncStatus.SYNCED
                } catch (e: Exception) {
                    Log.e("AppRepo", "Sync payment failed", e)
                    _syncStatus.value = SyncStatus.OFFLINE
                }
            }
        }
    }

    fun softDeletePayment(paymentId: String) {
        val now = DateUtils.todayIso()
        val p = _payments.value.find { it.id == paymentId } ?: return
        savePayment(p.copy(deletedAt = now))
    }

    fun restorePayment(paymentId: String) {
        val now = DateUtils.todayIso()
        val p = _payments.value.find { it.id == paymentId } ?: return
        savePayment(p.copy(deletedAt = null, updatedAt = now))
    }

    fun deletePaymentPermanently(paymentId: String) {
        val current = _payments.value.filter { it.id != paymentId }
        _payments.value = current
        localRepo.savePayments(current)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    firestoreRepo.deletePaymentPermanently(user.uid, paymentId)
                } catch (_: Exception) {}
            }
        }
    }

    // --- SETTINGS OPERATIONS ---
    fun updateSettings(settings: Settings) {
        val now = DateUtils.todayIso()
        val set = settings.copy(updatedAt = now)
        _settings.value = set
        localRepo.saveSettings(set)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    firestoreRepo.saveSettings(user.uid, set)
                } catch (_: Exception) {}
            }
        }
    }

    // --- MIGRATION & DEMO DATA ---
    fun hasLocalData(): Boolean = localRepo.hasLocalData()

    fun importLocalDataToCloud() {
        val user = _currentUser.value ?: return
        scope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            for (s in _students.value) firestoreRepo.saveStudent(user.uid, s)
            for (l in _classLogs.value) firestoreRepo.saveClassLog(user.uid, l)
            for (p in _payments.value) firestoreRepo.savePayment(user.uid, p)
            firestoreRepo.saveSettings(user.uid, _settings.value)
            _syncStatus.value = SyncStatus.SYNCED
        }
    }

    /**
     * Load demo data with 4 sample students as required:
     * 1) One with dues (Rahim Ahmed: ৳6,000 due)
     * 2) One with advance credit (Tasnim Jahan: ৳15,000 paid, -৳5,000 advance credit)
     * 3) One cleared (Sadman Sakib: ৳3,200 billed, ৳3,200 paid, balance ৳0)
     * 4) One archived (Nafisa Rahman: status "archived")
     * Plus realistic multi-month logs.
     */
    fun loadDemoData() {
        val now = DateUtils.todayIso()
        val demoStudents = listOf(
            Student(
                id = "std_rahim",
                name = "Rahim Ahmed",
                guardianName = "Dr. Anisur Ahmed",
                phone = "01711223344",
                subject = "Higher Math & Physics",
                grade = "Class 10",
                notes = "Preparing for SSC. Outstanding balance needs collection.",
                ratePerClass = 1000L,
                billByDuration = false,
                status = "active",
                createdAt = "2026-08-01",
                updatedAt = now
            ),
            Student(
                id = "std_tasnim",
                name = "Tasnim Jahan",
                guardianName = "Mrs. Sabina Yasmin",
                phone = "01819876543",
                subject = "English Literature",
                grade = "HSC 1st Year",
                notes = "Paid ৳15,000 upfront. Currently in advance credit.",
                ratePerClass = 1000L,
                billByDuration = false,
                status = "active",
                createdAt = "2026-08-15",
                updatedAt = now
            ),
            Student(
                id = "std_sadman",
                name = "Sadman Sakib",
                guardianName = "Md. Rafiqul Islam",
                phone = "01912345678",
                subject = "Chemistry",
                grade = "Class 9",
                notes = "Hourly mode. Cleared balance after every set of classes.",
                ratePerClass = 800L,
                billByDuration = true,
                status = "active",
                createdAt = "2026-09-01",
                updatedAt = now
            ),
            Student(
                id = "std_nafisa",
                name = "Nafisa Rahman",
                guardianName = "Engr. Faruq Rahman",
                phone = "01555667788",
                subject = "Biology",
                grade = "A-Level",
                notes = "Completed curriculum. Archived profile.",
                ratePerClass = 1200L,
                billByDuration = false,
                status = "archived",
                createdAt = "2026-07-01",
                updatedAt = now
            )
        )

        val demoLogs = listOf(
            // Rahim Ahmed: 8 classes held (৳8,000 billed), paid ৳2,000 => Due ৳6,000 (6 unpaid equivalent classes)
            ClassLog("log_r1", "std_rahim", "2026-08-05", 90, 1000L, "held", "Algebra chapter 1", "2026-08-05", now),
            ClassLog("log_r2", "std_rahim", "2026-08-12", 90, 1000L, "held", "Trigonometric ratios", "2026-08-12", now),
            ClassLog("log_r3", "std_rahim", "2026-09-02", 90, 1000L, "held", "Kinematics numericals", "2026-09-02", now),
            ClassLog("log_r4", "std_rahim", "2026-09-09", 90, 1000L, "held", "Newton's laws of motion", "2026-09-09", now),
            ClassLog("log_r5", "std_rahim", "2026-09-20", 90, 1000L, "held", "Vectors & Work Energy", "2026-09-20", now),
            ClassLog("log_r6", "std_rahim", "2026-10-02", 90, 1000L, "held", "Coordinate geometry", "2026-10-02", now),
            ClassLog("log_r7", "std_rahim", "2026-10-06", 90, 1000L, "held", "Straight lines formulas", "2026-10-06", now),
            ClassLog("log_r8", "std_rahim", "2026-10-10", 90, 1000L, "held", "Circles & Tangents test", "2026-10-10", now),
            ClassLog("log_r_cancel", "std_rahim", "2026-10-11", 90, 1000L, "cancelled", "Sick leave", "2026-10-11", now),

            // Tasnim Jahan: 10 classes held (৳10,000 billed), paid ৳15,000 => -৳5,000 advance credit
            ClassLog("log_t1", "std_tasnim", "2026-08-20", 60, 1000L, "held", "Poetry analysis", "2026-08-20", now),
            ClassLog("log_t2", "std_tasnim", "2026-08-27", 60, 1000L, "held", "Essay drafting", "2026-08-27", now),
            ClassLog("log_t3", "std_tasnim", "2026-09-04", 60, 1000L, "held", "Critical comprehension", "2026-09-04", now),
            ClassLog("log_t4", "std_tasnim", "2026-09-11", 60, 1000L, "held", "Shakespeare sonnets", "2026-09-11", now),
            ClassLog("log_t5", "std_tasnim", "2026-09-18", 60, 1000L, "held", "Macbeth Act 1", "2026-09-18", now),
            ClassLog("log_t6", "std_tasnim", "2026-09-25", 60, 1000L, "held", "Macbeth Act 2", "2026-09-25", now),
            ClassLog("log_t7", "std_tasnim", "2026-10-01", 60, 1000L, "held", "Grammar review", "2026-10-01", now),
            ClassLog("log_t8", "std_tasnim", "2026-10-05", 60, 1000L, "held", "Vocabulary drill", "2026-10-05", now),
            ClassLog("log_t9", "std_tasnim", "2026-10-08", 60, 1000L, "held", "Mock exam part 1", "2026-10-08", now),
            ClassLog("log_t10", "std_tasnim", "2026-10-12", 60, 1000L, "held", "Mock exam discussion", "2026-10-12", now),

            // Sadman Sakib: 4 classes held (4 x ৳800 = ৳3,200 billed), paid ৳3,200 => Balance ৳0 (Cleared)
            ClassLog("log_s1", "std_sadman", "2026-09-05", 60, 800L, "held", "Atomic structure", "2026-09-05", now),
            ClassLog("log_s2", "std_sadman", "2026-09-15", 60, 800L, "held", "Periodic properties", "2026-09-15", now),
            ClassLog("log_s3", "std_sadman", "2026-10-03", 60, 800L, "held", "Chemical bonds", "2026-10-03", now),
            ClassLog("log_s4", "std_sadman", "2026-10-10", 60, 800L, "held", "Stoichiometry basics", "2026-10-10", now),

            // Nafisa Rahman (archived): 3 classes held (৳3,600 billed), paid ৳3,600
            ClassLog("log_n1", "std_nafisa", "2026-07-08", 60, 1200L, "held", "Cell biology overview", "2026-07-08", now),
            ClassLog("log_n2", "std_nafisa", "2026-07-15", 60, 1200L, "held", "Genetics & DNA", "2026-07-15", now),
            ClassLog("log_n3", "std_nafisa", "2026-07-22", 60, 1200L, "held", "Final review", "2026-07-22", now)
        )

        val demoPayments = listOf(
            Payment("pay_r1", "std_rahim", 2000L, "2026-08-15", "bKash", "Partial token payment", "2026-08-15", now),
            Payment("pay_t1", "std_tasnim", 15000L, "2026-08-18", "bank", "3 months upfront tuition fee", "2026-08-18", now),
            Payment("pay_s1", "std_sadman", 1600L, "2026-09-16", "cash", "Cleared September classes", "2026-09-16", now),
            Payment("pay_s2", "std_sadman", 1600L, "2026-10-11", "Nagad", "Cleared October classes", "2026-10-11", now),
            Payment("pay_n1", "std_nafisa", 3600L, "2026-07-23", "cash", "Course completed & cleared", "2026-07-23", now)
        )

        _students.value = demoStudents
        _classLogs.value = demoLogs
        _payments.value = demoPayments
        localRepo.saveStudents(demoStudents)
        localRepo.saveClassLogs(demoLogs)
        localRepo.savePayments(demoPayments)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            importLocalDataToCloud()
        }
    }

    // --- BACKUP & RECOVERY ---
    private fun checkAndCreateDailySnapshot() {
        val today = DateUtils.todayIso()
        val snapshot = BackupSnapshot(
            schemaVersion = 1,
            exportedAt = today,
            date = today,
            students = _students.value,
            classLogs = _classLogs.value,
            payments = _payments.value,
            settings = _settings.value
        )
        localRepo.saveSnapshotToFile(snapshot)

        val user = _currentUser.value
        if (user != null) {
            scope.launch {
                try {
                    firestoreRepo.saveBackupSnapshot(user.uid, snapshot)
                } catch (_: Exception) {}
            }
        }
    }

    fun listSnapshots(): List<Pair<String, String>> = localRepo.listSnapshots()

    fun exportFullBackupJson(): String {
        val now = DateUtils.todayIso()
        val snapshot = BackupSnapshot(
            schemaVersion = 1,
            exportedAt = now,
            date = now,
            students = _students.value,
            classLogs = _classLogs.value,
            payments = _payments.value,
            settings = _settings.value
        )
        val updatedSet = _settings.value.copy(lastExportAt = now)
        updateSettings(updatedSet)
        return JsonHelper.backupToJson(snapshot)
    }

    fun restoreBackup(jsonString: String, mode: String) { // "merge" | "replace"
        checkAndCreateDailySnapshot()
        val imported = JsonHelper.backupFromJson(jsonString)

        if (mode == "replace") {
            _students.value = imported.students
            _classLogs.value = imported.classLogs
            _payments.value = imported.payments
            _settings.value = imported.settings
        } else {
            val existingStudentIds = _students.value.map { it.id }.toSet()
            val newStudents = _students.value + imported.students.filter { it.id !in existingStudentIds }

            val existingLogIds = _classLogs.value.map { it.id }.toSet()
            val newLogs = _classLogs.value + imported.classLogs.filter { it.id !in existingLogIds }

            val existingPaymentIds = _payments.value.map { it.id }.toSet()
            val newPayments = _payments.value + imported.payments.filter { it.id !in existingPaymentIds }

            _students.value = newStudents
            _classLogs.value = newLogs
            _payments.value = newPayments
            _settings.value = imported.settings
        }

        localRepo.saveStudents(_students.value)
        localRepo.saveClassLogs(_classLogs.value)
        localRepo.savePayments(_payments.value)
        localRepo.saveSettings(_settings.value)
        recomputeLedger()

        val user = _currentUser.value
        if (user != null) {
            importLocalDataToCloud()
        }
    }

    fun exportClassLogsCsv(): String {
        val sb = StringBuilder()
        sb.append("LogID,StudentID,StudentName,Date,DurationMin,Rate,Status,Note\n")
        val sMap = _students.value.associateBy { it.id }
        for (l in _classLogs.value.filter { it.deletedAt == null }) {
            val sName = sMap[l.studentId]?.name ?: "Unknown"
            sb.append("\"${l.id}\",\"${l.studentId}\",\"$sName\",\"${l.date}\",${l.durationMin ?: 0},${l.rate},\"${l.status}\",\"${l.note ?: ""}\"\n")
        }
        return sb.toString()
    }

    fun exportPaymentsCsv(): String {
        val sb = StringBuilder()
        sb.append("PaymentID,StudentID,StudentName,Date,Amount,Method,Note\n")
        val sMap = _students.value.associateBy { it.id }
        for (p in _payments.value.filter { it.deletedAt == null }) {
            val sName = sMap[p.studentId]?.name ?: "Unknown"
            sb.append("\"${p.id}\",\"${p.studentId}\",\"$sName\",\"${p.date}\",${p.amount},\"${p.method}\",\"${p.note ?: ""}\"\n")
        }
        return sb.toString()
    }

    fun clearAllData() {
        _students.value = emptyList()
        _classLogs.value = emptyList()
        _payments.value = emptyList()
        _studentBalances.value = emptyMap()
        localRepo.clearAll()
    }
}
