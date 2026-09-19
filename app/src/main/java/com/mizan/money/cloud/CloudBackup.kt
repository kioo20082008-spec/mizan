package com.mizan.money.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.mizan.money.BuildConfig
import com.mizan.money.data.BackupData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// Opt-in cloud backup/restore to Firebase Firestore. This is intentionally the
// only part of the app that touches the network: everything else works fully
// offline, and nothing is ever uploaded until the user signs in and taps
// "back up". Data is stored under users/{uid}/... so a future personal web
// dashboard can read the same account with the same email/password.
//
// Firebase is initialized manually from BuildConfig values (populated from
// local.properties / env) rather than the google-services plugin, so the app
// builds and runs unchanged when no Firebase project has been configured —
// isConfigured simply stays false and the UI hides the feature.
object CloudBackup {
    private const val USERS = "users"
    private const val COL_TRANSACTIONS = "transactions"
    private const val COL_BUDGETS = "budgets"
    private const val COL_GOALS = "goals"
    private const val COL_DEBTS = "debts"
    private const val COL_RECURRING = "recurringItems"
    private const val COL_CONTRIBUTIONS = "goalContributions"
    private const val META = "meta"

    // Firestore allows at most 500 writes per batch; stay comfortably under it.
    private const val BATCH_LIMIT = 400

    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank()

    private var app: FirebaseApp? = null
    private var authInstance: FirebaseAuth? = null
    private var firestoreInstance: FirebaseFirestore? = null

    // Safe to call more than once (MoneyApp.onCreate and SettingsDialog both
    // do). No-ops when unconfigured so it can never crash an unconfigured build.
    fun init(context: Context) {
        if (!isConfigured || app != null) return
        val fa = try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(
                context.applicationContext,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .apply {
                        if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank()) {
                            setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                        }
                        if (BuildConfig.FIREBASE_MESSAGING_SENDER_ID.isNotBlank()) {
                            setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)
                        }
                    }
                    .build()
            )
        }
        app = fa
        authInstance = FirebaseAuth.getInstance(fa)
        firestoreInstance = FirebaseFirestore.getInstance(fa)
    }

    // Google sign-in needs the web client id; without it the UI falls back to
    // email/password only.
    val isGoogleSignInConfigured: Boolean
        get() = isConfigured && BuildConfig.FIREBASE_GOOGLE_CLIENT_ID.isNotBlank()

    val isSignedIn: Boolean get() = authInstance?.currentUser != null
    val currentEmail: String? get() = authInstance?.currentUser?.email
        ?: authInstance?.currentUser?.displayName

    // Must be called with an Activity context (Credential Manager shows the
    // account picker as UI) and from the main dispatcher.
    suspend fun signInWithGoogle(context: Context): Result<Unit> {
        return try {
            val auth = authInstance ?: error("Cloud backup is not configured")
            val clientId = BuildConfig.FIREBASE_GOOGLE_CLIENT_ID
            if (clientId.isBlank()) error("Google sign-in is not configured")

            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                // false = show every Google account on the device, not only ones
                // already used with this app, so a first-time user can pick.
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credential = credentialManager.getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = authInstance ?: error("Cloud backup is not configured")
                auth.signInWithEmailAndPassword(email.trim(), password).await()
                Unit
            }
        }

    suspend fun signUp(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val auth = authInstance ?: error("Cloud backup is not configured")
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
                Unit
            }
        }

    fun signOut() {
        authInstance?.signOut()
    }

    // Replaces the cloud copy with the current device snapshot: matching
    // documents are overwritten, deleted locally-removed ones are pruned, and a
    // meta document records when it happened. Deletions are collected into the
    // same batches as writes where possible so a backup is only ever a few
    // network round-trips.
    suspend fun upload(data: BackupData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestoreInstance ?: error("Cloud backup is not configured")
            val uid = authInstance?.currentUser?.uid ?: error("Not signed in")
            val base = db.collection(USERS).document(uid)

            syncCollection(
                base.collection(COL_TRANSACTIONS),
                data.transactions.associate { it.id.toString() to CloudBackupMapper.transaction(it) },
            )
            syncCollection(
                base.collection(COL_BUDGETS),
                data.budgets.associate { CloudBackupMapper.budgetDocId(it) to CloudBackupMapper.budget(it) },
            )
            syncCollection(
                base.collection(COL_GOALS),
                data.goals.associate { it.id.toString() to CloudBackupMapper.goal(it) },
            )
            syncCollection(
                base.collection(COL_DEBTS),
                data.debts.associate { it.id.toString() to CloudBackupMapper.debt(it) },
            )
            syncCollection(
                base.collection(COL_RECURRING),
                data.recurringItems.associate { it.id.toString() to CloudBackupMapper.recurringItem(it) },
            )
            syncCollection(
                base.collection(COL_CONTRIBUTIONS),
                data.goalContributions.associate { it.id.toString() to CloudBackupMapper.contribution(it) },
            )

            base.collection(META).document("backup").set(
                mapOf(
                    "updatedAt" to System.currentTimeMillis(),
                    "version" to 1,
                    "transactionCount" to data.transactions.size,
                )
            ).await()
            Unit
        }
    }

    suspend fun download(): Result<BackupData> = withContext(Dispatchers.IO) {
        runCatching {
            val db = firestoreInstance ?: error("Cloud backup is not configured")
            val uid = authInstance?.currentUser?.uid ?: error("Not signed in")
            val base = db.collection(USERS).document(uid)

            BackupData(
                transactions = base.collection(COL_TRANSACTIONS).get().await().documents
                    .mapNotNull { it.data }.map { CloudBackupMapper.transactionFrom(it) },
                budgets = base.collection(COL_BUDGETS).get().await().documents
                    .mapNotNull { it.data }.map { CloudBackupMapper.budgetFrom(it) },
                goals = base.collection(COL_GOALS).get().await().documents
                    .mapNotNull { it.data }.map { CloudBackupMapper.goalFrom(it) },
                debts = base.collection(COL_DEBTS).get().await().documents
                    .mapNotNull { it.data }.map { CloudBackupMapper.debtFrom(it) },
                recurringItems = base.collection(COL_RECURRING).get().await().documents
                    .mapNotNull { it.data }.map { CloudBackupMapper.recurringItemFrom(it) },
                goalContributions = base.collection(COL_CONTRIBUTIONS).get().await().documents
                    .mapNotNull { it.data }.map { CloudBackupMapper.contributionFrom(it) },
            )
        }
    }

    private suspend fun syncCollection(
        col: CollectionReference,
        docs: Map<String, Map<String, Any?>>,
    ) {
        val existing = col.get().await().documents.map { it.id }.toSet()
        val stale = existing - docs.keys

        val ops = ArrayList<Pair<DocumentReference, Map<String, Any?>?>>(stale.size + docs.size)
        stale.forEach { ops.add(col.document(it) to null) }
        docs.forEach { (id, map) -> ops.add(col.document(id) to map) }

        ops.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = col.firestore.batch()
            chunk.forEach { (ref, map) ->
                if (map == null) batch.delete(ref) else batch.set(ref, map)
            }
            batch.commit().await()
        }
    }
}
