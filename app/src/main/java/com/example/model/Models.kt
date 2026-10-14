package com.example.model

data class Student(
    val id: String,
    val name: String,
    val guardianName: String? = null,
    val phone: String,
    val subject: String = "",
    val grade: String? = null,
    val notes: String? = null,
    val ratePerClass: Long = 0L, // integer
    val billByDuration: Boolean = false, // per-student toggle for hourly mode
    val status: String = "active", // "active" | "archived"
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null
)

data class ClassLog(
    val id: String,
    val studentId: String,
    val date: String, // YYYY-MM-DD, default today
    val durationMin: Int? = null,
    val rate: Long, // integer, copied from student.ratePerClass at log time or computed, editable per log
    val status: String = "held", // "held" | "cancelled"
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null
)

data class Payment(
    val id: String,
    val studentId: String,
    val amount: Long, // integer
    val date: String, // YYYY-MM-DD
    val method: String = "cash", // "cash" | "bKash" | "Nagad" | "bank" | "other"
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null
)

data class Settings(
    val currency: String = "BDT",
    val currencySymbol: String = "৳",
    val theme: String = "system", // "system" | "light" | "dark"
    val reminderTemplate: String = "Hello {name}, this is a gentle reminder that tuition fees for {month} ({classCount} classes) amounting to {currency}{amount} are due. Thank you!",
    val lastExportAt: String? = null,
    val lastUsedPaymentMethod: String = "cash",
    val updatedAt: String? = null
)

data class BackupSnapshot(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val date: String = "",
    val students: List<Student> = emptyList(),
    val classLogs: List<ClassLog> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val settings: Settings = Settings()
)

data class StudentBalanceInfo(
    val student: Student,
    val totalBilled: Long,
    val totalPaid: Long,
    val balance: Long, // > 0: due; < 0: advance credit; == 0: clear
    val heldCount: Int,
    val unpaidClassEquivalent: Double
)

data class MonthlyStatement(
    val student: Student,
    val monthKey: String, // YYYY-MM
    val previousBalance: Long, // carried forward from prior months
    val heldLogs: List<ClassLog>,
    val monthSubtotal: Long,
    val monthPayments: List<Payment>,
    val monthPaymentsTotal: Long,
    val closingBalance: Long
)

data class ActivityItem(
    val id: String,
    val type: String, // "class_log" | "payment"
    val studentId: String,
    val studentName: String,
    val date: String,
    val amountOrRate: Long,
    val title: String,
    val subtitle: String,
    val classLog: ClassLog? = null,
    val payment: Payment? = null
)
