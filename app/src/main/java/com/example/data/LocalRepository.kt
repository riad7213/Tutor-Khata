package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BackupSnapshot
import com.example.model.ClassLog
import com.example.model.Payment
import com.example.model.Settings
import com.example.model.Student
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tuition_ledger_local", Context.MODE_PRIVATE)

    fun loadStudents(): List<Student> {
        val json = prefs.getString("students", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Student>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) list.add(JsonHelper.studentFromJson(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveStudents(students: List<Student>) {
        val arr = JSONArray()
        for (s in students) arr.put(JsonHelper.studentToJson(s))
        prefs.edit().putString("students", arr.toString()).apply()
    }

    fun loadClassLogs(): List<ClassLog> {
        val json = prefs.getString("class_logs", null) ?: prefs.getString("sessions", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<ClassLog>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) list.add(JsonHelper.classLogFromJson(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveClassLogs(logs: List<ClassLog>) {
        val arr = JSONArray()
        for (l in logs) arr.put(JsonHelper.classLogToJson(l))
        prefs.edit().putString("class_logs", arr.toString()).apply()
    }

    fun loadPayments(): List<Payment> {
        val json = prefs.getString("payments", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Payment>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) list.add(JsonHelper.paymentFromJson(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePayments(payments: List<Payment>) {
        val arr = JSONArray()
        for (p in payments) arr.put(JsonHelper.paymentToJson(p))
        prefs.edit().putString("payments", arr.toString()).apply()
    }

    fun loadSettings(): Settings {
        val json = prefs.getString("settings", null) ?: return Settings()
        return try {
            JsonHelper.settingsFromJson(JSONObject(json))
        } catch (_: Exception) {
            Settings()
        }
    }

    fun saveSettings(settings: Settings) {
        prefs.edit().putString("settings", JsonHelper.settingsToJson(settings).toString()).apply()
    }

    fun hasLocalData(): Boolean {
        return prefs.contains("students") && loadStudents().isNotEmpty()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // Local file snapshot backup storage
    fun saveSnapshotToFile(snapshot: BackupSnapshot) {
        try {
            val dir = File(context.filesDir, "snapshots")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "snapshot_${snapshot.date}.json")
            file.writeText(JsonHelper.backupToJson(snapshot))

            // Keep max 7 snapshots
            val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
            if (files.size > 7) {
                files.take(files.size - 7).forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    fun listSnapshots(): List<Pair<String, String>> {
        val dir = File(context.filesDir, "snapshots")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.map { Pair(it.name.removePrefix("snapshot_").removeSuffix(".json"), it.readText()) }
            ?: emptyList()
    }
}
