package com.example.data

import com.example.model.BackupSnapshot
import com.example.model.ClassLog
import com.example.model.Payment
import com.example.model.Settings
import com.example.model.Student
import org.json.JSONArray
import org.json.JSONObject

object JsonHelper {

    fun studentToJson(student: Student): JSONObject {
        return JSONObject().apply {
            put("id", student.id)
            put("name", student.name)
            put("guardianName", student.guardianName ?: JSONObject.NULL)
            put("phone", student.phone)
            put("subject", student.subject)
            put("grade", student.grade ?: JSONObject.NULL)
            put("notes", student.notes ?: JSONObject.NULL)
            put("ratePerClass", student.ratePerClass)
            put("billByDuration", student.billByDuration)
            put("status", student.status)
            put("createdAt", student.createdAt)
            put("updatedAt", student.updatedAt)
            put("deletedAt", student.deletedAt ?: JSONObject.NULL)
        }
    }

    fun studentFromJson(obj: JSONObject): Student {
        // Support fallback from old fields if present
        val fallbackRate = if (obj.has("ratePerClass") && !obj.isNull("ratePerClass")) {
            obj.getLong("ratePerClass")
        } else if (obj.has("perSessionFee") && !obj.isNull("perSessionFee")) {
            obj.getLong("perSessionFee")
        } else if (obj.has("monthlyFee") && !obj.isNull("monthlyFee")) {
            obj.getLong("monthlyFee")
        } else {
            0L
        }

        return Student(
            id = obj.getString("id"),
            name = obj.getString("name"),
            guardianName = if (obj.isNull("guardianName")) null else obj.optString("guardianName"),
            phone = obj.optString("phone", ""),
            subject = obj.optString("subject", ""),
            grade = if (obj.isNull("grade")) null else obj.optString("grade"),
            notes = if (obj.isNull("notes")) null else obj.optString("notes"),
            ratePerClass = fallbackRate,
            billByDuration = obj.optBoolean("billByDuration", false),
            status = obj.optString("status", "active"),
            createdAt = obj.optString("createdAt", ""),
            updatedAt = obj.optString("updatedAt", ""),
            deletedAt = if (obj.isNull("deletedAt")) null else obj.optString("deletedAt")
        )
    }

    fun classLogToJson(log: ClassLog): JSONObject {
        return JSONObject().apply {
            put("id", log.id)
            put("studentId", log.studentId)
            put("date", log.date)
            if (log.durationMin != null) put("durationMin", log.durationMin)
            put("rate", log.rate)
            put("status", log.status)
            put("note", log.note ?: JSONObject.NULL)
            put("createdAt", log.createdAt)
            put("updatedAt", log.updatedAt)
            put("deletedAt", log.deletedAt ?: JSONObject.NULL)
        }
    }

    fun classLogFromJson(obj: JSONObject): ClassLog {
        val rateVal = if (obj.has("rate") && !obj.isNull("rate")) {
            obj.getLong("rate")
        } else if (obj.has("feeOverride") && !obj.isNull("feeOverride")) {
            obj.getLong("feeOverride")
        } else {
            0L
        }

        return ClassLog(
            id = obj.getString("id"),
            studentId = obj.getString("studentId"),
            date = obj.getString("date"),
            durationMin = if (obj.has("durationMin") && !obj.isNull("durationMin")) obj.getInt("durationMin") else null,
            rate = rateVal,
            status = obj.optString("status", "held"),
            note = if (obj.isNull("note")) null else obj.optString("note"),
            createdAt = obj.optString("createdAt", ""),
            updatedAt = obj.optString("updatedAt", ""),
            deletedAt = if (obj.isNull("deletedAt")) null else obj.optString("deletedAt")
        )
    }

    fun paymentToJson(payment: Payment): JSONObject {
        return JSONObject().apply {
            put("id", payment.id)
            put("studentId", payment.studentId)
            put("amount", payment.amount)
            put("date", payment.date)
            put("method", payment.method)
            put("note", payment.note ?: JSONObject.NULL)
            put("createdAt", payment.createdAt)
            put("updatedAt", payment.updatedAt)
            put("deletedAt", payment.deletedAt ?: JSONObject.NULL)
        }
    }

    fun paymentFromJson(obj: JSONObject): Payment {
        return Payment(
            id = obj.getString("id"),
            studentId = obj.getString("studentId"),
            amount = obj.getLong("amount"),
            date = obj.getString("date"),
            method = obj.optString("method", "cash"),
            note = if (obj.isNull("note")) null else obj.optString("note"),
            createdAt = obj.optString("createdAt", ""),
            updatedAt = obj.optString("updatedAt", ""),
            deletedAt = if (obj.isNull("deletedAt")) null else obj.optString("deletedAt")
        )
    }

    fun settingsToJson(settings: Settings): JSONObject {
        return JSONObject().apply {
            put("currency", settings.currency)
            put("currencySymbol", settings.currencySymbol)
            put("theme", settings.theme)
            put("reminderTemplate", settings.reminderTemplate)
            put("lastExportAt", settings.lastExportAt ?: JSONObject.NULL)
            put("lastUsedPaymentMethod", settings.lastUsedPaymentMethod)
            put("updatedAt", settings.updatedAt ?: JSONObject.NULL)
        }
    }

    fun settingsFromJson(obj: JSONObject): Settings {
        return Settings(
            currency = obj.optString("currency", "BDT"),
            currencySymbol = obj.optString("currencySymbol", "৳"),
            theme = obj.optString("theme", "system"),
            reminderTemplate = obj.optString(
                "reminderTemplate",
                "Hello {name}, this is a gentle reminder that tuition fees for {month} ({classCount} classes) amounting to {currency}{amount} are due. Thank you!"
            ),
            lastExportAt = if (obj.isNull("lastExportAt")) null else obj.optString("lastExportAt"),
            lastUsedPaymentMethod = obj.optString("lastUsedPaymentMethod", "cash"),
            updatedAt = if (obj.isNull("updatedAt")) null else obj.optString("updatedAt")
        )
    }

    fun backupToJson(snapshot: BackupSnapshot): String {
        val root = JSONObject().apply {
            put("schemaVersion", snapshot.schemaVersion)
            put("exportedAt", snapshot.exportedAt)
            put("date", snapshot.date)
            put("settings", settingsToJson(snapshot.settings))
            val sArr = JSONArray()
            for (s in snapshot.students) sArr.put(studentToJson(s))
            put("students", sArr)
            val lArr = JSONArray()
            for (l in snapshot.classLogs) lArr.put(classLogToJson(l))
            put("classLogs", lArr)
            val pArr = JSONArray()
            for (p in snapshot.payments) pArr.put(paymentToJson(p))
            put("payments", pArr)
        }
        return root.toString(2)
    }

    fun backupFromJson(jsonString: String): BackupSnapshot {
        val root = JSONObject(jsonString)
        val schemaVer = root.optInt("schemaVersion", 1)
        val exportedAt = root.optString("exportedAt", "")
        val date = root.optString("date", "")
        val settings = if (root.has("settings")) settingsFromJson(root.getJSONObject("settings")) else Settings()

        val students = mutableListOf<Student>()
        val sArr = root.optJSONArray("students")
        if (sArr != null) {
            for (i in 0 until sArr.length()) {
                val item = sArr.optJSONObject(i)
                if (item != null) students.add(studentFromJson(item))
            }
        }

        val classLogs = mutableListOf<ClassLog>()
        // Look for "classLogs" first, with fallback to legacy "sessions"
        val lArr = root.optJSONArray("classLogs") ?: root.optJSONArray("sessions")
        if (lArr != null) {
            for (i in 0 until lArr.length()) {
                val item = lArr.optJSONObject(i)
                if (item != null) classLogs.add(classLogFromJson(item))
            }
        }

        val payments = mutableListOf<Payment>()
        val pArr = root.optJSONArray("payments")
        if (pArr != null) {
            for (i in 0 until pArr.length()) {
                val item = pArr.optJSONObject(i)
                if (item != null) payments.add(paymentFromJson(item))
            }
        }

        return BackupSnapshot(
            schemaVersion = schemaVer,
            exportedAt = exportedAt,
            date = date,
            students = students,
            classLogs = classLogs,
            payments = payments,
            settings = settings
        )
    }
}
