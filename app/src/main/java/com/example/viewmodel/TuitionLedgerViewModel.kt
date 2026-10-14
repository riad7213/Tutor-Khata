package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthManager
import com.example.data.AppRepository
import com.example.data.FirestoreRepository
import com.example.data.LocalRepository
import com.example.data.SyncStatus
import com.example.model.ActivityItem
import com.example.model.ClassLog
import com.example.model.MonthlyStatement
import com.example.model.Payment
import com.example.model.Settings
import com.example.model.Student
import com.example.model.StudentBalanceInfo
import com.example.utils.DateUtils
import com.example.utils.LedgerEngine
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class MonthIncomeItem(
    val monthKey: String,
    val monthLabel: String,
    val amount: Long
)

data class MonthClassCount(
    val monthKey: String,
    val monthLabel: String,
    val count: Int
)

class TuitionLedgerViewModel(application: Application) : AndroidViewModel(application) {
    val authManager = AuthManager(application)
    private val localRepo = LocalRepository(application)
    private val firestoreRepo = FirestoreRepository(application)
    val repository = AppRepository(application, localRepo, firestoreRepo)

    val currentUser: StateFlow<FirebaseUser?> = authManager.currentUser
    val isAuthLoading: StateFlow<Boolean> = authManager.isLoading
    val authError: StateFlow<String?> = authManager.errorMessage

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus
    val settings: StateFlow<Settings> = repository.settings

    // All active non-deleted entities
    val students: StateFlow<List<Student>> = repository.students.map { list ->
        list.filter { it.deletedAt == null }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val classLogs: StateFlow<List<ClassLog>> = repository.classLogs.map { list ->
        list.filter { it.deletedAt == null }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payments: StateFlow<List<Payment>> = repository.payments.map { list ->
        list.filter { it.deletedAt == null }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val studentBalances: StateFlow<Map<String, StudentBalanceInfo>> = repository.studentBalances

    // Trash lists (soft-deleted items)
    val trashStudents = repository.students.map { list -> list.filter { it.deletedAt != null } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val trashClassLogs = repository.classLogs.map { list -> list.filter { it.deletedAt != null } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val trashPayments = repository.payments.map { list -> list.filter { it.deletedAt != null } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI snackbar message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Undo delete cache
    private var lastDeletedType: String? = null
    private var lastDeletedId: String? = null

    init {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                repository.onUserChanged(user)
            }
        }
        viewModelScope.launch {
            authManager.trySilentSignIn()
        }
    }

    // --- HOME SCREEN COMPUTED METRICS ---
    val totalOutstanding: StateFlow<Long> = studentBalances.map { map ->
        map.values.filter { it.balance > 0L }.sumOf { it.balance }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val thisMonthHeldClassCount: StateFlow<Int> = classLogs.map { list ->
        val currentMonth = DateUtils.currentMonthKey()
        list.count { it.date.startsWith(currentMonth) && it.status == "held" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val thisMonthEarned: StateFlow<Long> = classLogs.map { list ->
        val currentMonth = DateUtils.currentMonthKey()
        list.filter { it.date.startsWith(currentMonth) && it.status == "held" }.sumOf { it.rate }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val thisMonthReceived: StateFlow<Long> = payments.map { list ->
        val currentMonth = DateUtils.currentMonthKey()
        list.filter { it.date.startsWith(currentMonth) }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val needsCollectionStudents: StateFlow<List<StudentBalanceInfo>> = studentBalances.map { map ->
        map.values
            .filter { it.balance > 0L }
            .sortedByDescending { it.balance }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Recent activity: combined last 10 logs and payments
    val recentActivity: StateFlow<List<ActivityItem>> = combine(classLogs, payments, students) { logs, pays, stds ->
        val studentMap = stds.associateBy { it.id }
        val logItems = logs.map { log ->
            val sName = studentMap[log.studentId]?.name ?: "Unknown Student"
            val durStr = if (log.durationMin != null) " • ${log.durationMin} mins" else ""
            ActivityItem(
                id = log.id,
                type = "class_log",
                studentId = log.studentId,
                studentName = sName,
                date = log.date,
                amountOrRate = log.rate,
                title = "Class with $sName",
                subtitle = "${DateUtils.formatDisplayDate(log.date)}$durStr • ${log.status.replaceFirstChar { it.uppercase() }}",
                classLog = log
            )
        }
        val payItems = pays.map { p ->
            val sName = studentMap[p.studentId]?.name ?: "Unknown Student"
            ActivityItem(
                id = p.id,
                type = "payment",
                studentId = p.studentId,
                studentName = sName,
                date = p.date,
                amountOrRate = p.amount,
                title = "Payment from $sName",
                subtitle = "${DateUtils.formatDisplayDate(p.date)} • ${p.method}",
                payment = p
            )
        }
        (logItems + payItems)
            .sortedWith(compareByDescending<ActivityItem> { it.date }.thenByDescending { it.id })
            .take(10)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val needsBackupReminder: StateFlow<Boolean> = settings.map { set ->
        val last = set.lastExportAt
        if (last == null) {
            true
        } else {
            val calLast = DateUtils.parseIsoDate(last)
            val calToday = Calendar.getInstance()
            val diffDays = (calToday.timeInMillis - calLast.timeInMillis) / (1000 * 60 * 60 * 24)
            diffDays >= 30
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Analytics: Monthly Income for last 6 months
    val last6MonthsIncome: StateFlow<List<MonthIncomeItem>> = payments.map { payList ->
        val result = mutableListOf<MonthIncomeItem>()
        val cal = Calendar.getInstance()
        val mFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val lFormat = SimpleDateFormat("MMM", Locale.US)

        for (i in 5 downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -i)
            val key = mFormat.format(c.time)
            val label = lFormat.format(c.time)
            val sum = payList.filter { it.date.startsWith(key) }.sumOf { it.amount }
            result.add(MonthIncomeItem(key, label, sum))
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Analytics: Classes held per month for last 6 months
    val last6MonthsClasses: StateFlow<List<MonthClassCount>> = classLogs.map { logList ->
        val result = mutableListOf<MonthClassCount>()
        val cal = Calendar.getInstance()
        val mFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val lFormat = SimpleDateFormat("MMM", Locale.US)

        for (i in 5 downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.MONTH, -i)
            val key = mFormat.format(c.time)
            val label = lFormat.format(c.time)
            val count = logList.count { it.date.startsWith(key) && it.status == "held" }
            result.add(MonthClassCount(key, label, count))
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val averagePerClass: StateFlow<Long> = classLogs.map { list ->
        val heldList = list.filter { it.status == "held" }
        if (heldList.isNotEmpty()) {
            heldList.sumOf { it.rate } / heldList.size
        } else 0L
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    // --- ACTIONS ---

    fun signInWithGoogle() {
        viewModelScope.launch {
            authManager.signInWithGoogle()
        }
    }

    fun signOut() {
        authManager.signOut()
    }

    fun dismissAuthError() {
        authManager.clearError()
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    // Quick-Log a class
    fun logClass(
        studentId: String,
        date: String = DateUtils.todayIso(),
        durationMin: Int? = null,
        rateOverride: Long? = null,
        note: String? = null,
        status: String = "held"
    ) {
        val student = students.value.find { it.id == studentId }
        val effectiveRate = rateOverride ?: if (student != null) {
            LedgerEngine.computeDefaultRate(student, durationMin)
        } else 0L

        val log = ClassLog(
            id = "log_" + UUID.randomUUID().toString().substring(0, 8),
            studentId = studentId,
            date = date,
            durationMin = durationMin,
            rate = effectiveRate,
            status = status,
            note = note?.ifEmpty { null },
            createdAt = DateUtils.todayIso(),
            updatedAt = DateUtils.todayIso()
        )
        repository.saveClassLog(log)
        val sName = student?.name ?: "Student"
        showSnackbar("Class logged for $sName (${LedgerEngine.formatMoney(effectiveRate, settings.value.currencySymbol)})")
    }

    // Save/Update Student
    fun saveStudent(student: Student) {
        val s = if (student.id.isEmpty()) {
            student.copy(id = "std_" + UUID.randomUUID().toString().substring(0, 8))
        } else student
        repository.saveStudent(s)
        showSnackbar("Student saved: ${s.name}")
    }

    fun softDeleteStudent(studentId: String) {
        lastDeletedType = "student"
        lastDeletedId = studentId
        repository.softDeleteStudent(studentId)
        showSnackbar("Student moved to Trash")
    }

    fun softDeleteClassLog(logId: String) {
        lastDeletedType = "class_log"
        lastDeletedId = logId
        repository.softDeleteClassLog(logId)
        showSnackbar("Class log moved to Trash")
    }

    fun savePayment(payment: Payment) {
        val p = if (payment.id.isEmpty()) {
            payment.copy(id = "pay_" + UUID.randomUUID().toString().substring(0, 8))
        } else payment
        repository.savePayment(p)

        // Algorithm E: Compute new balance and show feedback
        val student = students.value.find { it.id == p.studentId }
        if (student != null) {
            val updatedBalanceInfo = LedgerEngine.computeStudentBalance(
                student = student,
                classLogs = repository.classLogs.value,
                payments = repository.payments.value
            )
            val balanceMsg = when {
                updatedBalanceInfo.balance > 0 -> "Balance now ${LedgerEngine.formatMoney(updatedBalanceInfo.balance, settings.value.currencySymbol)}"
                updatedBalanceInfo.balance < 0 -> "Advance credit ${LedgerEngine.formatMoney(-updatedBalanceInfo.balance, settings.value.currencySymbol)}"
                else -> "Cleared 🎉"
            }
            showSnackbar("Payment recorded! $balanceMsg")
        } else {
            showSnackbar("Payment of ${LedgerEngine.formatMoney(p.amount, settings.value.currencySymbol)} recorded!")
        }
    }

    fun softDeletePayment(paymentId: String) {
        lastDeletedType = "payment"
        lastDeletedId = paymentId
        repository.softDeletePayment(paymentId)
        showSnackbar("Payment moved to Trash")
    }

    fun undoLastDelete() {
        val type = lastDeletedType
        val id = lastDeletedId
        if (type != null && id != null) {
            when (type) {
                "student" -> repository.restoreStudent(id)
                "class_log" -> repository.restoreClassLog(id)
                "payment" -> repository.restorePayment(id)
            }
            lastDeletedType = null
            lastDeletedId = null
            showSnackbar("Item restored")
        }
    }

    fun restoreItem(type: String, id: String) {
        when (type) {
            "student" -> repository.restoreStudent(id)
            "class_log", "session" -> repository.restoreClassLog(id)
            "payment" -> repository.restorePayment(id)
        }
        showSnackbar("Restored")
    }

    fun deleteItemPermanently(type: String, id: String) {
        when (type) {
            "student" -> repository.deleteStudentPermanently(id)
            "class_log", "session" -> repository.deleteClassLogPermanently(id)
            "payment" -> repository.deletePaymentPermanently(id)
        }
        showSnackbar("Deleted forever")
    }

    fun updateSettings(newSettings: Settings) {
        repository.updateSettings(newSettings)
        showSnackbar("Settings updated")
    }

    fun loadDemoData() {
        repository.loadDemoData()
        showSnackbar("Demo data loaded (4 students)")
    }

    fun importLocalDataToCloud() {
        repository.importLocalDataToCloud()
        showSnackbar("Local data synced to cloud")
    }

    fun restoreBackup(jsonString: String, mode: String) {
        try {
            repository.restoreBackup(jsonString, mode)
            showSnackbar("Backup restored ($mode mode)")
        } catch (e: Exception) {
            showSnackbar("Restore failed: ${e.message}")
        }
    }

    fun clearAllData() {
        repository.clearAllData()
        showSnackbar("All data cleared")
    }

    fun getMonthlyStatement(student: Student, monthKey: String): MonthlyStatement {
        return LedgerEngine.generateMonthlyStatement(
            student = student,
            monthKey = monthKey,
            classLogs = classLogs.value,
            payments = payments.value
        )
    }

    fun hasClassLoggedOnDate(studentId: String, dateIso: String): Boolean {
        return LedgerEngine.hasClassLoggedOnDate(studentId, dateIso, classLogs.value)
    }
}
