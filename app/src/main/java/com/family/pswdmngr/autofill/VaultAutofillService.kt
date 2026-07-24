package com.family.pswdmngr.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.family.pswdmngr.R
import com.family.pswdmngr.data.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fills login forms in other apps and browsers. Works on Android 9 (API 28)+.
 * Only offers data when the vault is currently unlocked — otherwise it stays
 * silent (no lock-screen bypass surface).
 */
class VaultAutofillService : AutofillService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
            ?: return callback.onSuccess(null)

        val fields = ParsedFields()
        parseStructure(structure, fields)
        if (fields.username == null && fields.password == null) {
            return callback.onSuccess(null)
        }

        if (!VaultSession.isUnlocked) {
            // Don't leak anything while locked
            return callback.onSuccess(null)
        }

        val domain = fields.webDomain ?: structure.activityComponent?.packageName ?: ""
        scope.launch {
            try {
                val matches = VaultSession.dao().matchDomain(simplify(domain)).take(5)
                if (matches.isEmpty()) return@launch callback.onSuccess(null)

                val response = FillResponse.Builder().apply {
                    matches.forEach { entry ->
                        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
                            setTextViewText(R.id.autofill_title, entry.title)
                            setTextViewText(R.id.autofill_subtitle, entry.username)
                        }
                        addDataset(Dataset.Builder().apply {
                            fields.username?.let {
                                setValue(it, AutofillValue.forText(entry.username), presentation)
                            }
                            fields.password?.let {
                                setValue(it, AutofillValue.forText(entry.password), presentation)
                            }
                        }.build())
                    }
                }.build()
                callback.onSuccess(response)
            } catch (e: Exception) {
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Saving new credentials from other apps: out of scope v1, user adds in-app
        callback.onSuccess()
    }

    private class ParsedFields {
        var username: AutofillId? = null
        var password: AutofillId? = null
        var webDomain: String? = null
    }

    private fun parseStructure(structure: AssistStructure, out: ParsedFields) {
        for (i in 0 until structure.windowNodeCount) {
            parseNode(structure.getWindowNodeAt(i).rootViewNode, out)
        }
    }

    private fun parseNode(node: AssistStructure.ViewNode, out: ParsedFields) {
        node.webDomain?.takeIf { it.isNotBlank() }?.let { out.webDomain = it }

        val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
        val idEntry = (node.idEntry ?: "").lowercase()
        val hintText = (node.hint ?: "").lowercase()
        val inputType = node.inputType

        val isPassword = hints.any { it.contains("password") } ||
                idEntry.contains("pass") || hintText.contains("pass") ||
                (inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                (inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0

        val isUsername = hints.any {
            it.contains("username") || it.contains("email") || it == View.AUTOFILL_HINT_EMAIL_ADDRESS
        } || idEntry.contains("user") || idEntry.contains("email") ||
                hintText.contains("email") || hintText.contains("user")

        node.autofillId?.let { id ->
            when {
                isPassword && out.password == null -> out.password = id
                isUsername && out.username == null -> out.username = id
                else -> {}
            }
        }
        for (i in 0 until node.childCount) parseNode(node.getChildAt(i), out)
    }

    private fun simplify(domain: String): String =
        domain.removePrefix("www.").split(".").let {
            if (it.size >= 2) it[it.size - 2] else domain
        }
}
