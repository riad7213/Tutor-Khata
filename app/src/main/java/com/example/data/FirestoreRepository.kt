package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.BackupSnapshot
import com.example.model.ClassLog
import com.example.model.Payment
import com.example.model.Settings
import com.example.model.Student
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class FirestoreRepository(private val context: Context) {
    private val TAG = "FirestoreRepo"

    private val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        val instance = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder().build()
            )
            .build()
        instance.firestoreSettings = settings
        instance
    }

    private fun requireUserId(userId: String?): String {
        return userId ?: error("Operation requires authenticated user")
    }

    // Students: users/{uid}/students/{id}
    fun observeStudents(userId: String): Flow<List<Student>> = callbackFlow {
        val uid = requireUserId(userId)
        val listener: ListenerRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("students")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing students: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val json = JSONObject(data)
                            json.put("id", doc.id)
                            JsonHelper.studentFromJson(json)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse student ${doc.id}", e)
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveStudent(userId: String, student: Student) {
        val uid = requireUserId(userId)
        val json = JsonHelper.studentToJson(student)
        val map = jsonToMap(json)
        firestore.collection("users").document(uid)
            .collection("students").document(student.id)
            .set(map, SetOptions.merge())
            .await()
    }

    suspend fun deleteStudentPermanently(userId: String, studentId: String) {
        val uid = requireUserId(userId)
        firestore.collection("users").document(uid)
            .collection("students").document(studentId)
            .delete()
            .await()
    }

    // ClassLogs: users/{uid}/classLogs/{id}
    fun observeClassLogs(userId: String): Flow<List<ClassLog>> = callbackFlow {
        val uid = requireUserId(userId)
        val listener: ListenerRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("classLogs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing classLogs: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val json = JSONObject(data)
                            json.put("id", doc.id)
                            JsonHelper.classLogFromJson(json)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse classLog ${doc.id}", e)
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveClassLog(userId: String, classLog: ClassLog) {
        val uid = requireUserId(userId)
        val json = JsonHelper.classLogToJson(classLog)
        val map = jsonToMap(json)
        firestore.collection("users").document(uid)
            .collection("classLogs").document(classLog.id)
            .set(map, SetOptions.merge())
            .await()
    }

    suspend fun deleteClassLogPermanently(userId: String, logId: String) {
        val uid = requireUserId(userId)
        firestore.collection("users").document(uid)
            .collection("classLogs").document(logId)
            .delete()
            .await()
    }

    // Payments: users/{uid}/payments/{id}
    fun observePayments(userId: String): Flow<List<Payment>> = callbackFlow {
        val uid = requireUserId(userId)
        val listener: ListenerRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("payments")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing payments: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            val json = JSONObject(data)
                            json.put("id", doc.id)
                            JsonHelper.paymentFromJson(json)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse payment ${doc.id}", e)
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun savePayment(userId: String, payment: Payment) {
        val uid = requireUserId(userId)
        val json = JsonHelper.paymentToJson(payment)
        val map = jsonToMap(json)
        firestore.collection("users").document(uid)
            .collection("payments").document(payment.id)
            .set(map, SetOptions.merge())
            .await()
    }

    suspend fun deletePaymentPermanently(userId: String, paymentId: String) {
        val uid = requireUserId(userId)
        firestore.collection("users").document(uid)
            .collection("payments").document(paymentId)
            .delete()
            .await()
    }

    // Settings: users/{uid}/settings/main
    fun observeSettings(userId: String): Flow<Settings> = callbackFlow {
        val uid = requireUserId(userId)
        val listener: ListenerRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("settings")
            .document("main")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing settings: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val data = snapshot.data ?: return@addSnapshotListener
                        val json = JSONObject(data)
                        trySend(JsonHelper.settingsFromJson(json))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse settings", e)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun saveSettings(userId: String, settings: Settings) {
        val uid = requireUserId(userId)
        val json = JsonHelper.settingsToJson(settings)
        val map = jsonToMap(json)
        firestore.collection("users").document(uid)
            .collection("settings").document("main")
            .set(map, SetOptions.merge())
            .await()
    }

    // Snapshots: users/{uid}/backups/{YYYY-MM-DD}
    suspend fun saveBackupSnapshot(userId: String, snapshot: BackupSnapshot) {
        val uid = requireUserId(userId)
        val rawJson = JsonHelper.backupToJson(snapshot)
        val data = mapOf(
            "date" to snapshot.date,
            "exportedAt" to snapshot.exportedAt,
            "payload" to rawJson
        )
        firestore.collection("users").document(uid)
            .collection("backups").document(snapshot.date)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun fetchBackupSnapshots(userId: String): List<Pair<String, String>> {
        val uid = requireUserId(userId)
        val query = firestore.collection("users").document(uid)
            .collection("backups")
            .get()
            .await()
        return query.documents.mapNotNull { doc ->
            val payload = doc.getString("payload")
            if (payload != null) Pair(doc.id, payload) else null
        }.sortedByDescending { it.first }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            if (value != JSONObject.NULL) {
                map[key] = when (value) {
                    is JSONObject -> jsonToMap(value)
                    is org.json.JSONArray -> jsonArrayToList(value)
                    else -> value
                }
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: org.json.JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until arr.length()) {
            val item = arr.get(i)
            if (item != JSONObject.NULL) {
                list.add(
                    when (item) {
                        is JSONObject -> jsonToMap(item)
                        is org.json.JSONArray -> jsonArrayToList(item)
                        else -> item
                    }
                )
            }
        }
        return list
    }
}
