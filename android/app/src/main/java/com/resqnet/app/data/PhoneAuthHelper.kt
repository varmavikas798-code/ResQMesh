package com.resqnet.app.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PhoneAuthHelper {
    fun activity(context: Context): Activity =
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
            ?: throw IllegalStateException("Phone verification needs an activity")

    suspend fun sendCode(activity: Activity, phone: String): String = suspendCancellableCoroutine { cont ->
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {}
            override fun onVerificationFailed(error: FirebaseException) {
                if (cont.isActive) cont.resumeWithException(error)
            }
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                if (cont.isActive) cont.resume(verificationId)
            }
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
        )
    }

    suspend fun idToken(verificationId: String, code: String): String = suspendCancellableCoroutine { cont ->
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { signIn ->
                if (!signIn.isSuccessful || signIn.result?.user == null) {
                    if (cont.isActive) cont.resumeWithException(signIn.exception ?: IllegalStateException("Verification failed"))
                    return@addOnCompleteListener
                }
                signIn.result!!.user!!.getIdToken(true).addOnCompleteListener { result ->
                    val idToken = result.result?.token
                    if (!result.isSuccessful || idToken.isNullOrBlank()) {
                        if (cont.isActive) cont.resumeWithException(result.exception ?: IllegalStateException("Token unavailable"))
                    } else if (cont.isActive) {
                        cont.resume(idToken)
                    }
                }
            }
    }
}
