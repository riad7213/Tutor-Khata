package com.example.utils

import com.example.model.ClassLog
import com.example.model.MonthlyStatement
import com.example.model.Payment
import com.example.model.Student
import com.example.model.StudentBalanceInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.round

object LedgerEngine {

    /**
     * Algorithm A: Pure ledger balance calculation.
     * Only logs with status == "held" and deletedAt == null count towards totalBilled.
     * Payments with deletedAt == null count towards totalPaid.
     * balance = totalBilled - totalPaid (> 0: due; < 0: advance credit; == 0: clear)
     */
    fun computeStudentBalance(
        student: Student,
        classLogs: List<ClassLog>,
        payments: List<Payment>
    ): StudentBalanceInfo {
        val heldLogs = classLogs.filter {
            it.studentId == student.id && it.status == "held" && it.deletedAt == null
        }
        val totalBilled = heldLogs.sumOf { it.rate }

        val activePayments = payments.filter {
            it.studentId == student.id && it.deletedAt == null
        }
        val totalPaid = activePayments.sumOf { it.amount }

        val balance = totalBilled - totalPaid
        val unpaidEquiv = if (student.ratePerClass > 0L && balance > 0L) {
            balance.toDouble() / student.ratePerClass.toDouble()
        } else {
            0.0
        }

        return StudentBalanceInfo(
            student = student,
            totalBilled = totalBilled,
            totalPaid = totalPaid,
            balance = balance,
            heldCount = heldLogs.size,
            unpaidClassEquivalent = unpaidEquiv
        )
    }

    /**
     * Compute balances for all students.
     */
    fun computeAllBalances(
        students: List<Student>,
        classLogs: List<ClassLog>,
        payments: List<Payment>
    ): Map<String, StudentBalanceInfo> {
        val map = mutableMapOf<String, StudentBalanceInfo>()
        for (student in students.filter { it.deletedAt == null }) {
            map[student.id] = computeStudentBalance(student, classLogs, payments)
        }
        return map
    }

    /**
     * Algorithm B: Optional hourly mode calculation.
     * If student.billByDuration is on and durationMin is provided:
     * rate = ratePerHour * durationMin / 60 rounded to nearest integer.
     */
    fun computeDefaultRate(student: Student, durationMin: Int?): Long {
        return if (student.billByDuration && durationMin != null && durationMin > 0) {
            round(student.ratePerClass.toDouble() * durationMin.toDouble() / 60.0).toLong()
        } else {
            student.ratePerClass
        }
    }

    /**
     * Algorithm C: Monthly statement generator.
     * For a student and month (YYYY-MM):
     * - previous balance carried forward from prior months
     * - held logs in that month (date, note, rate)
     * - month subtotal
     * - payments in that month
     * - closing balance
     */
    fun generateMonthlyStatement(
        student: Student,
        monthKey: String, // e.g. "2026-10"
        classLogs: List<ClassLog>,
        payments: List<Payment>
    ): MonthlyStatement {
        val monthStart = if (monthKey.length == 7) "$monthKey-01" else monthKey

        // Prior held logs before month start
        val priorHeldLogs = classLogs.filter {
            it.studentId == student.id &&
                    it.status == "held" &&
                    it.deletedAt == null &&
                    it.date < monthStart
        }
        val priorBilled = priorHeldLogs.sumOf { it.rate }

        // Prior payments before month start
        val priorPayments = payments.filter {
            it.studentId == student.id &&
                    it.deletedAt == null &&
                    it.date < monthStart
        }
        val priorPaid = priorPayments.sumOf { it.amount }

        val previousBalance = priorBilled - priorPaid

        // Month held logs
        val monthHeldLogs = classLogs.filter {
            it.studentId == student.id &&
                    it.status == "held" &&
                    it.deletedAt == null &&
                    it.date.startsWith(monthKey)
        }.sortedBy { it.date }
        val monthSubtotal = monthHeldLogs.sumOf { it.rate }

        // Month payments
        val monthPayments = payments.filter {
            it.studentId == student.id &&
                    it.deletedAt == null &&
                    it.date.startsWith(monthKey)
        }.sortedBy { it.date }
        val monthPaymentsTotal = monthPayments.sumOf { it.amount }

        val closingBalance = previousBalance + monthSubtotal - monthPaymentsTotal

        return MonthlyStatement(
            student = student,
            monthKey = monthKey,
            previousBalance = previousBalance,
            heldLogs = monthHeldLogs,
            monthSubtotal = monthSubtotal,
            monthPayments = monthPayments,
            monthPaymentsTotal = monthPaymentsTotal,
            closingBalance = closingBalance
        )
    }

    /**
     * Format a clean, human-readable statement text to share via WhatsApp, SMS, or Clipboard.
     */
    fun formatStatementText(statement: MonthlyStatement, currencySymbol: String = "৳"): String {
        val monthName = DateUtils.formatMonthName(statement.monthKey)
        val sb = StringBuilder()
        sb.append("📋 *Tuition Statement - ${statement.student.name}*\n")
        sb.append("🗓️ Month: $monthName\n")
        if (statement.student.subject.isNotBlank()) {
            sb.append("📚 Subject: ${statement.student.subject}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━\n")

        // Previous balance
        sb.append("Previous Balance Carried Forward: ${formatMoney(statement.previousBalance, currencySymbol)}\n\n")

        // Classes taken
        sb.append("Classes Taken (${statement.heldLogs.size}):\n")
        if (statement.heldLogs.isEmpty()) {
            sb.append("  (No classes held in this month)\n")
        } else {
            statement.heldLogs.forEachIndexed { idx, log ->
                val noteStr = if (!log.note.isNullOrBlank()) " (${log.note})" else ""
                val durStr = if (log.durationMin != null) " [${log.durationMin}m]" else ""
                sb.append("  ${idx + 1}. ${DateUtils.formatDisplayDate(log.date)}$durStr: ${formatMoney(log.rate, currencySymbol)}$noteStr\n")
            }
        }
        sb.append("Month Subtotal: ${formatMoney(statement.monthSubtotal, currencySymbol)}\n\n")

        // Payments
        sb.append("Payments Received (${statement.monthPayments.size}):\n")
        if (statement.monthPayments.isEmpty()) {
            sb.append("  (No payments received in this month)\n")
        } else {
            statement.monthPayments.forEachIndexed { idx, p ->
                val noteStr = if (!p.note.isNullOrBlank()) " (${p.note})" else ""
                sb.append("  ${idx + 1}. ${DateUtils.formatDisplayDate(p.date)} [${p.method}]: ${formatMoney(p.amount, currencySymbol)}$noteStr\n")
            }
        }
        sb.append("Total Paid This Month: ${formatMoney(statement.monthPaymentsTotal, currencySymbol)}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")

        val closingLabel = when {
            statement.closingBalance > 0 -> "🔴 Closing Balance Due"
            statement.closingBalance < 0 -> "🔵 Advance Credit"
            else -> "🟢 Account Cleared"
        }
        val absVal = if (statement.closingBalance < 0) -statement.closingBalance else statement.closingBalance
        sb.append("*$closingLabel: ${formatMoney(absVal, currencySymbol)}*\n")
        sb.append("\nThank you!")
        return sb.toString()
    }

    /**
     * Format money with thousands separator, e.g. ৳12,500
     */
    fun formatMoney(amount: Long, symbol: String = "৳"): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return if (amount < 0) {
            "-$symbol${formatter.format(-amount)}"
        } else {
            "$symbol${formatter.format(amount)}"
        }
    }

    /**
     * Format unpaid equivalent classes label, e.g. "~2.5 classes" or "1 class"
     */
    fun formatUnpaidClasses(equiv: Double): String {
        return if (equiv <= 0.0) {
            "0 classes"
        } else if (kotlin.math.abs(equiv - equiv.toInt()) < 0.05) {
            val count = equiv.toInt()
            "$count ${if (count == 1) "class" else "classes"}"
        } else {
            String.format(Locale.US, "~%.1f classes", equiv)
        }
    }

    /**
     * Normalize Bangladesh phone number (+880) by default
     */
    fun normalizePhone(phone: String): String {
        val cleaned = phone.replace("[^0-9+]".toRegex(), "")
        return when {
            cleaned.startsWith("+880") -> cleaned.substring(1)
            cleaned.startsWith("880") -> cleaned
            cleaned.startsWith("0") -> "880" + cleaned.substring(1)
            cleaned.isNotEmpty() -> "880$cleaned"
            else -> ""
        }
    }

    /**
     * Algorithm F: Format reminder message template
     */
    fun formatReminder(
        template: String,
        studentName: String,
        amount: Long,
        currencySymbol: String,
        monthName: String = DateUtils.formatMonthName(DateUtils.currentMonthKey()),
        classCount: Int = 0
    ): String {
        val absAmount = if (amount < 0) -amount else amount
        return template
            .replace("{name}", studentName)
            .replace("{amount}", formatMoney(absAmount, "").trim())
            .replace("{currency}", currencySymbol)
            .replace("{month}", monthName)
            .replace("{classCount}", classCount.toString())
    }

    /**
     * WhatsApp deep link
     */
    fun getWhatsAppUrl(phone: String, message: String): String {
        val normalized = normalizePhone(phone)
        val encodedText = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
        return "https://wa.me/$normalized?text=$encodedText"
    }

    /**
     * SMS URI
     */
    fun getSmsUri(phone: String, message: String): String {
        val normalized = normalizePhone(phone)
        val encodedText = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
        return "sms:+$normalized?body=$encodedText"
    }

    /**
     * Check if a student already has a class logged for a given date.
     * Used for double-log prevention warning.
     */
    fun hasClassLoggedOnDate(studentId: String, dateIso: String, classLogs: List<ClassLog>): Boolean {
        return classLogs.any {
            it.studentId == studentId && it.date == dateIso && it.deletedAt == null
        }
    }
}
