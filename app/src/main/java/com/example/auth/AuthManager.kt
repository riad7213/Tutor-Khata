package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    private fun getWebClientId(): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else "59665051243-dia21svuvdnjkgubmot2rk7pc81n5dmp.apps.googleusercontent.com"
        } catch (_: Exception) {
            "59665051243-dia21svuvdnjkgubmot2rk7pc81n5dmp.apps.googleusercontent.com"
        }
    }

    suspend fun signInWithGoogle(): Boolean {
        _isLoading.value = true
        _errorMessage.value = null
        val serverClientId = getWebClientId()

        try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            _currentUser.value = authResult.user
            _isLoading.value = false
            return true
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthManager", "User cancelled Google Sign-In: ${e.message}")
            _isLoading.value = false
            return false
        } catch (e: GetCredentialException) {
            Log.e("AuthManager", "CredentialManager failed: ${e.message}", e)
            _errorMessage.value = "Sign-in error: ${e.message}"
            _isLoading.value = false
            return false
        } catch (e: Exception) {
            Log.e("AuthManager", "Google Sign-In failed: ${e.message}", e)
            _errorMessage.value = "Authentication failed: ${e.localizedMessage ?: "Unknown error"}"
            _isLoading.value = false
            return false
        }
    }

    suspend fun trySilentSignIn(): Boolean {
        if (auth.currentUser != null) {
            _currentUser.value = auth.currentUser
            return true
        }

        val serverClientId = getWebClientId()
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            _currentUser.value = authResult.user
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
