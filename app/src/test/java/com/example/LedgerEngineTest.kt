package com.example

import com.example.model.ClassLog
import com.example.model.Payment
import com.example.model.Student
import com.example.utils.LedgerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerEngineTest {

    @Test
    fun testBalanceCalculation_partialPayment() {
        val student = Student(
            id = "s_partial",
            name = "Rahim Ahmed",
            phone = "01711223344",
            ratePerClass = 1000L,
            createdAt = "2026-10-01",
            updatedAt = "2026-10-01"
        )
        // 3 held classes = 3000 billed
        val logs = listOf(
            ClassLog("l1", student.id, "2026-10-02", 60, 1000L, "held", createdAt = "2026-10-02", updatedAt = "2026-10-02"),
            ClassLog("l2", student.id, "2026-10-05", 60, 1000L, "held", createdAt = "2026-10-05", updatedAt = "2026-10-05"),
            ClassLog("l3", student.id, "2026-10-08", 60, 1000L, "held", createdAt = "2026-10-08", updatedAt = "2026-10-08")
        )
        // Partial payment of 2000
        val payments = listOf(
            Payment("p1", student.id, 2000L, "2026-10-06", "bKash", createdAt = "2026-10-06", updatedAt = "2026-10-06")
        )

        val balanceInfo = LedgerEngine.computeStudentBalance(student, logs, payments)
        assertEquals(3000L, balanceInfo.totalBilled)
        assertEquals(2000L, balanceInfo.totalPaid)
        assertEquals(1000L, balanceInfo.balance) // Due 1000
        assertEquals(1.0, balanceInfo.unpaidClassEquivalent, 0.001)
    }

    @Test
    fun testBalanceCalculation_overpaymentCredit() {
        val student = Student(
            id = "s_overpay",
            name = "Tasnim Jahan",
            phone = "01819876543",
            ratePerClass = 1000L,
            createdAt = "2026-10-01",
            updatedAt = "2026-10-01"
        )
        // 2 held classes = 2000 billed
        val logs = listOf(
            ClassLog("l1", student.id, "2026-10-02", 60, 1000L, "held", createdAt = "2026-10-02", updatedAt = "2026-10-02"),
            ClassLog("l2", student.id, "2026-10-05", 60, 1000L, "held", createdAt = "2026-10-05", updatedAt = "2026-10-05")
        )
        // Advance payment of 5000
        val payments = listOf(
            Payment("p1", student.id, 5000L, "2026-10-01", "bank", createdAt = "2026-10-01", updatedAt = "2026-10-01")
        )

        val balanceInfo = LedgerEngine.computeStudentBalance(student, logs, payments)
        assertEquals(2000L, balanceInfo.totalBilled)
        assertEquals(5000L, balanceInfo.totalPaid)
        assertEquals(-3000L, balanceInfo.balance) // Negative balance means advance credit
        assertEquals(0.0, balanceInfo.unpaidClassEquivalent, 0.001)
    }

    @Test
    fun testBalanceCalculation_rateChangeNotAffectingOldLogs() {
        val studentInitial = Student(
            id = "s_rate_change",
            name = "Sadman Sakib",
            phone = "01912345678",
            ratePerClass = 800L,
            createdAt = "2026-09-01",
            updatedAt = "2026-09-01"
        )
        // Log logged at old rate 800
        val oldLog = ClassLog("l1", studentInitial.id, "2026-09-05", 60, rate = 800L, status = "held", createdAt = "2026-09-05", updatedAt = "2026-09-05")

        // Tutor increases student's rate to 1200 later
        val studentUpdated = studentInitial.copy(ratePerClass = 1200L)
        // New log at new rate 1200
        val newLog = ClassLog("l2", studentUpdated.id, "2026-10-02", 60, rate = 1200L, status = "held", createdAt = "2026-10-02", updatedAt = "2026-10-02")

        val logs = listOf(oldLog, newLog)
        val balanceInfo = LedgerEngine.computeStudentBalance(studentUpdated, logs, emptyList())

        // Old log stayed 800, new log is 1200 -> total 2000
        assertEquals(2000L, balanceInfo.totalBilled)
        assertEquals(2000L, balanceInfo.balance)
    }

    @Test
    fun testBalanceCalculation_deletedLogExcluded() {
        val student = Student(
            id = "s_del",
            name = "Deleted Test",
            phone = "01500000000",
            ratePerClass = 1000L,
            createdAt = "2026-10-01",
            updatedAt = "2026-10-01"
        )
        val heldLog = ClassLog("l1", student.id, "2026-10-02", 60, 1000L, "held", createdAt = "2026-10-02", updatedAt = "2026-10-02")
        val deletedLog = ClassLog("l2", student.id, "2026-10-05", 60, 1000L, "held", deletedAt = "2026-10-06", createdAt = "2026-10-05", updatedAt = "2026-10-05")
        val cancelledLog = ClassLog("l3", student.id, "2026-10-08", 60, 1000L, "cancelled", createdAt = "2026-10-08", updatedAt = "2026-10-08")

        val logs = listOf(heldLog, deletedLog, cancelledLog)
        val balanceInfo = LedgerEngine.computeStudentBalance(student, logs, emptyList())

        // Only heldLog without deletedAt counts
        assertEquals(1000L, balanceInfo.totalBilled)
        assertEquals(1, balanceInfo.heldCount)
        assertEquals(1000L, balanceInfo.balance)
    }

    @Test
    fun testMonthlyStatement_carryForwardBalanceAndMonthBoundaries() {
        val student = Student(
            id = "s_statement",
            name = "Statement Test",
            phone = "01600000000",
            ratePerClass = 1000L,
            createdAt = "2026-08-01",
            updatedAt = "2026-08-01"
        )

        // August: 2 classes (2000), 1 payment (1500) -> closing balance 500
        // September: 3 classes (3000), 1 payment (2000) -> carry 500 + 3000 - 2000 = 1500 closing
        // October: 1 class (1000), 1 payment (1500) -> carry 1500 + 1000 - 1500 = 1000 closing
        val logs = listOf(
            ClassLog("l_aug1", student.id, "2026-08-10", 60, 1000L, "held", createdAt = "2026-08-10", updatedAt = "2026-08-10"),
            ClassLog("l_aug2", student.id, "2026-08-20", 60, 1000L, "held", createdAt = "2026-08-20", updatedAt = "2026-08-20"),
            ClassLog("l_sep1", student.id, "2026-09-05", 60, 1000L, "held", createdAt = "2026-09-05", updatedAt = "2026-09-05"),
            ClassLog("l_sep2", student.id, "2026-09-12", 60, 1000L, "held", createdAt = "2026-09-12", updatedAt = "2026-09-12"),
            ClassLog("l_sep3", student.id, "2026-09-19", 60, 1000L, "held", createdAt = "2026-09-19", updatedAt = "2026-09-19"),
            ClassLog("l_oct1", student.id, "2026-10-04", 60, 1000L, "held", createdAt = "2026-10-04", updatedAt = "2026-10-04")
        )

        val payments = listOf(
            Payment("p_aug", student.id, 1500L, "2026-08-25", "cash", createdAt = "2026-08-25", updatedAt = "2026-08-25"),
            Payment("p_sep", student.id, 2000L, "2026-09-28", "bKash", createdAt = "2026-09-28", updatedAt = "2026-09-28"),
            Payment("p_oct", student.id, 1500L, "2026-10-10", "Nagad", createdAt = "2026-10-10", updatedAt = "2026-10-10")
        )

        // September statement
        val sepStatement = LedgerEngine.generateMonthlyStatement(student, "2026-09", logs, payments)
        assertEquals(500L, sepStatement.previousBalance) // 2000 - 1500 from August
        assertEquals(3000L, sepStatement.monthSubtotal)
        assertEquals(2000L, sepStatement.monthPaymentsTotal)
        assertEquals(1500L, sepStatement.closingBalance) // 500 + 3000 - 2000 = 1500

        // October statement
        val octStatement = LedgerEngine.generateMonthlyStatement(student, "2026-10", logs, payments)
        assertEquals(1500L, octStatement.previousBalance) // Carried forward from Aug & Sep
        assertEquals(1000L, octStatement.monthSubtotal)
        assertEquals(1500L, octStatement.monthPaymentsTotal)
        assertEquals(1000L, octStatement.closingBalance) // 1500 + 1000 - 1500 = 1000
    }

    @Test
    fun testHourlyBillingMode_durationCalculation() {
        val studentHourly = Student(
            id = "s_hourly",
            name = "Hourly Student",
            phone = "01700000000",
            ratePerClass = 600L, // 600 per hour
            billByDuration = true,
            createdAt = "2026-10-01",
            updatedAt = "2026-10-01"
        )

        // 90 minutes -> 600 * 90 / 60 = 900
        val rate90 = LedgerEngine.computeDefaultRate(studentHourly, 90)
        assertEquals(900L, rate90)

        // 45 minutes -> 600 * 45 / 60 = 450
        val rate45 = LedgerEngine.computeDefaultRate(studentHourly, 45)
        assertEquals(450L, rate45)

        // Non-hourly student: stays fixed at ratePerClass
        val studentFixed = studentHourly.copy(billByDuration = false)
        val rateFixed = LedgerEngine.computeDefaultRate(studentFixed, 90)
        assertEquals(600L, rateFixed)
    }

    @Test
    fun testZeroRateStudent_andFormatting() {
        val studentFree = Student(
            id = "s_free",
            name = "Scholarship Student",
            phone = "01799999999",
            ratePerClass = 0L,
            createdAt = "2026-10-01",
            updatedAt = "2026-10-01"
        )
        val logs = listOf(
            ClassLog("l1", studentFree.id, "2026-10-02", 60, 0L, "held", createdAt = "2026-10-02", updatedAt = "2026-10-02")
        )
        val balanceInfo = LedgerEngine.computeStudentBalance(studentFree, logs, emptyList())
        assertEquals(0L, balanceInfo.balance)
        assertEquals(0.0, balanceInfo.unpaidClassEquivalent, 0.001)

        val text = LedgerEngine.formatUnpaidClasses(balanceInfo.unpaidClassEquivalent)
        assertEquals("0 classes", text)
    }

    @Test
    fun testPhoneNormalizationAndReminders() {
        // Bangladesh phone format: 01711223344 -> 8801711223344
        val normalized = LedgerEngine.normalizePhone("01711223344")
        assertEquals("8801711223344", normalized)

        val alreadyWithCountryCode = LedgerEngine.normalizePhone("+8801819876543")
        assertEquals("8801819876543", alreadyWithCountryCode)

        // Reminder template replacement: {name}, {amount}, {month}, {currency}, {classCount}
        val template = "Dear {name}, tuition fees of {currency}{amount} for {month} ({classCount} classes) are due."
        val reminder = LedgerEngine.formatReminder(
            template = template,
            studentName = "Rahim",
            amount = 5000L,
            currencySymbol = "৳",
            monthName = "October 2026",
            classCount = 5
        )
        assertTrue(reminder.contains("Dear Rahim"))
        assertTrue(reminder.contains("৳5,000"))
        assertTrue(reminder.contains("October 2026"))
        assertTrue(reminder.contains("5 classes"))
    }
}
