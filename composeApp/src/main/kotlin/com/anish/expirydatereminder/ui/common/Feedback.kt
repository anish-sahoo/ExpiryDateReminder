package com.anish.expirydatereminder.ui.common

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.anish.expirydatereminder.logging.Log

/**
 * One place for short user-facing confirmations.
 *
 * Inside a screen with a Scaffold, prefer a Snackbar: it can carry an action (the undo on
 * delete depends on that) and it respects insets. A Toast is used where there is no
 * Scaffold to host one, or where the message should outlive the screen that triggered it,
 * such as confirmations fired from a bottom sheet as it closes.
 */
class Feedback(private val context: Context) {

    fun toast(message: String, long: Boolean = false) {
        Log.d(TAG, "toast: $message")
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    fun toast(resId: Int, vararg args: Any) {
        toast(context.getString(resId, *args))
    }

    private companion object {
        const val TAG = "Feedback"
    }
}

@Composable
fun rememberFeedback(): Feedback = Feedback(LocalContext.current)
