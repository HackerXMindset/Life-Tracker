package com.lifetracker.app.data

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Signing in to Gmail with Google's own Android sign-in. Google finds the app from its package name and signing key (the Android
 * client made in your Google project), so nothing is typed in here, and the sign-in is kept by Google, not by this app.
 */
object GmailAuth {
    const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    fun request(email: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE)))
        if (email != null) builder.setAccount(Account(email, "com.google"))
        return builder.build()
    }

    suspend fun <T> await(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Words for what went wrong, in the way that says what to do. */
    fun explain(e: Throwable): String {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            10 -> "Google does not recognise this app. In your Google project the Android client must have the package name com.lifetracker.app and the SHA-1 shown in Kairos."
            12501, 16 -> "Cancelled."
            7 -> "Could not reach Google. Check the internet and try again."
            null -> "Google sign-in failed (${e.javaClass.simpleName})."
            else -> "Google sign-in failed (code $code)."
        }
    }

    /** A working token for [email] without asking anything. Throws when you have to allow it again. */
    suspend fun silentToken(context: Context, email: String): String {
        val result = try {
            await(Identity.getAuthorizationClient(context.applicationContext).authorize(request(email)))
        } catch (e: ApiException) {
            throw MediaException(explain(e), offline = e.statusCode == 7)
        }
        if (result.hasResolution()) {
            throw MediaException("Gmail needs you to allow Kairos again for $email. Open Settings, Gmail purchases, and press Reconnect.")
        }
        return result.accessToken ?: throw MediaException("Google did not give access for $email. Press Reconnect in Gmail purchases.")
    }
}
