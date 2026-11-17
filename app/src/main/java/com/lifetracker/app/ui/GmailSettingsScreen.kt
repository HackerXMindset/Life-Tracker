package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.lifetracker.app.data.GmailAccounts
import com.lifetracker.app.data.GmailAuth
import com.lifetracker.app.data.GmailClient
import com.lifetracker.app.data.GmailLog
import com.lifetracker.app.data.GmailRunState
import com.lifetracker.app.data.GmailSettings
import com.lifetracker.app.data.GmailSync
import com.lifetracker.app.data.GmailSyncWorker
import com.lifetracker.app.data.KIND_INCOME
import com.lifetracker.app.data.MediaException
import com.lifetracker.app.data.Merchants
import com.lifetracker.app.data.MoneySettings
import com.lifetracker.app.data.TakeoutImport
import com.lifetracker.app.data.TakeoutPrefs
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The signing key every build of the app uses; the Android client in your Google project must carry it. */
private const val SHA1 = "6D:BD:EE:59:85:76:6F:31:D9:79:70:44:F9:D1:D3:F1:AB:A3:82:80"
private const val PACKAGE = "com.lifetracker.app"

private fun day(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** Settings → Gmail purchases: connect Gmail accounts, how far back to read, and what needs a look. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GmailSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    val accounts = remember { GmailAccounts(context) }
    val settings = remember { GmailSettings(context) }
    val log = remember { GmailLog(context) }
    val currency = remember { MoneySettings(context).currency }
    var tick by remember { mutableIntStateOf(0) }
    val emails = remember(tick) { accounts.list() }
    val unsure = remember(tick) { log.unsure().sortedByDescending { it.atMs } }
    val recent = remember(tick) { log.recent().sortedByDescending { it.atMs } }
    val ignored = remember(tick) { settings.ignored().sorted() }
    val last = remember(tick) { GmailRunState(context).lastLine }
    var message by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var months by remember { mutableIntStateOf(settings.months) }

    fun finishConnect(result: AuthorizationResult) {
        val token = result.accessToken
        if (token == null) {
            message = "Google did not give access. Try again."
            return
        }
        scope.launch {
            try {
                val email = GmailClient.profileEmail(token) ?: throw MediaException("Could not tell which Gmail address that was.")
                accounts.add(email)
                GmailSyncWorker.schedule(context)
                message = "$email is connected. Reading has started."
                tick++
                runCatching { GmailSync.run(context) }
                tick++
            } catch (e: MediaException) {
                message = e.message ?: "Could not finish connecting Gmail."
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val data = res.data
        if (data == null) {
            message = "Cancelled."
        } else {
            try {
                finishConnect(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
            } catch (e: ApiException) {
                message = GmailAuth.explain(e)
            }
        }
    }

    val takeout by TakeoutImport.status.collectAsState()
    val takeoutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) TakeoutImport.start(context, uris)
    }

    fun connect(email: String?) {
        message = ""
        Identity.getAuthorizationClient(context).authorize(GmailAuth.request(email))
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) {
                    launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    finishConnect(r)
                }
            }
            .addOnFailureListener { e -> message = GmailAuth.explain(e) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Gmail purchases", "Fill the Money tab from your email", onBack) }
        item {
            Text(
                "Kairos reads the receipts, order and payment emails in the Gmail accounts you connect, and adds each purchase, payment or refund to the Money tab with the shop's name, the total and the day. " +
                    "It only reads (it cannot send, change or delete anything), everything stays on this phone, and the same purchase in several emails (ordered, then delivered) is added once. " +
                    "Delete an entry and it does not come back. When the total is not certain, the email waits in a short list below.",
                fontSize = 13.sp, color = p.ink2,
            )
        }
        if (message.isNotEmpty()) item { Text(message, fontSize = 13.sp, color = p.ink, fontWeight = FontWeight.SemiBold) }

        // ---- Accounts ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gmail accounts", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    if (emails.isEmpty()) Text("None connected yet.", fontSize = 13.sp, color = p.ink2)
                    emails.forEach { email ->
                        val c = accounts.cursor(email, System.currentTimeMillis())
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(email, fontSize = 14.sp, color = p.ink, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (c.done) "History finished." else "Read back to ${day(c.oldestMs)}.",
                                fontSize = 12.sp, color = p.ink2,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaperButton("Reconnect", { connect(email) }, style = PaperButtonStyle.Ghost, small = true)
                                PaperButton("Remove", { accounts.remove(email); tick++ }, style = PaperButtonStyle.Ghost, small = true)
                            }
                        }
                    }
                    PaperButton("Add a Gmail", { connect(null) }, small = true)
                    Text(
                        "Google asks you to allow Kairos. Because the app is your own and not reviewed by Google, it says \"Google hasn't verified this app\": press Advanced, then Go to Kairos. " +
                            "You can add as many Gmail accounts as you like, one at a time. Removing an account only stops reading it; to take back Google's permission, remove Kairos in your Google account's security settings.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
            }
        }

        // ---- How far back, and reading now ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("How far back to read", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GmailSettings.MONTH_CHOICES.forEach { (value, label) ->
                            PaperPill(label, months == value, {
                                months = value
                                settings.months = value
                                emails.forEach { accounts.restartHistory(it, System.currentTimeMillis()) }
                                tick++
                            })
                        }
                    }
                    Text(
                        "New mail is read every 15 minutes or so. Older mail is read a few hundred emails at a time, newest first, until the start you chose. \"Read now\" does it all in one go while you wait.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                    PaperButton(if (reading) "Reading…" else "Read now", {
                        reading = true
                        message = ""
                        scope.launch {
                            var rounds = 0
                            do {
                                val out = runCatching { GmailSync.run(context) }.getOrNull()
                                progress = out?.line ?: ""
                                rounds++
                                tick++
                            } while (out != null && out.problems.isEmpty() && !out.allDone && rounds < 80 && isActive)
                            reading = false
                        }
                    }, small = true, enabled = !reading && emails.isNotEmpty())
                    val shown = if (reading || progress.isNotEmpty()) progress else last
                    if (shown.isNotEmpty()) Text(shown, fontSize = 12.5.sp, color = p.ink)
                }
            }
        }

        // ---- Google Takeout ----
        item {
            val takeoutPrefs = remember { TakeoutPrefs(context) }
            val shown = if (takeout.running || takeout.line.isNotEmpty()) takeout.line else takeoutPrefs.lastLine
            val canUndo = !takeout.running && takeoutPrefs.lastRun != 0L
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Import a Google Takeout file", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        "For all your old mail at once, with no sign-in. On takeout.google.com press Deselect all, tick Mail (you can pick just some labels, such as Purchases), and export. " +
                            "Google emails you when the file is ready, which can take hours for a big mailbox. Download it to this phone, then choose it here. " +
                            "Pick the .zip as it is, or the .mbox inside it; several files at once are fine. Several Gmail accounts: import each one's file.",
                        fontSize = 12.5.sp, color = p.ink2,
                    )
                    Text(
                        "It reads the file as it goes, so a very large one works, but it can take a while: keep Kairos open until it says it has finished. " +
                            "If it stops, start it again; nothing is added twice. Mail also found live from Gmail, or by text message, is added once.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (takeout.running) {
                            PaperButton("Stop", { TakeoutImport.stop() }, style = PaperButtonStyle.Ghost, small = true)
                        } else {
                            PaperButton("Choose file(s)", { takeoutPicker.launch(arrayOf("*/*")) }, small = true)
                        }
                        if (canUndo) {
                            PaperButton("Undo the last import", { scope.launch { runCatching { TakeoutImport.undoLast(context) }; tick++ } }, style = PaperButtonStyle.Ghost, small = true)
                        }
                    }
                    takeout.fraction?.takeIf { takeout.running }?.let { Text("${(it * 100).toInt()}%", fontSize = 12.5.sp, color = p.ink, fontWeight = FontWeight.SemiBold) }
                    if (shown.isNotEmpty()) Text(shown, fontSize = 12.5.sp, color = p.ink)
                }
            }
        }

        // ---- To check ----
        if (unsure.isNotEmpty()) {
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("To check (${unsure.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        Text("These looked like money but Kairos was not sure of the amount, so they are not on the Money tab yet.", fontSize = 12.5.sp, color = p.ink2)
                        unsure.take(15).forEach { u ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${u.merchant} · ${day(u.atMs)}", fontSize = 14.sp, color = p.ink, fontWeight = FontWeight.SemiBold)
                                Text(u.subject, fontSize = 12.5.sp, color = p.ink2)
                                val guess = u.amount?.let { "Guess: $currency%.2f".format(it) + if (u.kind == KIND_INCOME) " (refund)" else "" } ?: "No amount found."
                                Text("${u.reason}. $guess", fontSize = 12.sp, color = p.ink2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaperButton("Add", { scope.launch { runCatching { GmailSync.accept(context, u.account, u.msgId) }; tick++ } }, small = true, enabled = u.amount != null)
                                    PaperButton("Skip", { scope.launch { runCatching { GmailSync.dismiss(context, u.account, u.msgId) }; tick++ } }, style = PaperButtonStyle.Ghost, small = true)
                                    PaperButton("Never this sender", {
                                        settings.ignore(Merchants.domainOf(u.from))
                                        scope.launch { runCatching { GmailSync.dismiss(context, u.account, u.msgId) }; tick++ }
                                    }, style = PaperButtonStyle.Ghost, small = true)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Recently added ----
        if (recent.isNotEmpty()) {
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Recently added", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        Text("Taken off here, an entry stays off. A wrong sender can be switched off for good.", fontSize = 12.5.sp, color = p.ink2)
                        recent.take(20).forEach { r ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "${r.merchant} · $currency%.2f".format(r.amount) + if (r.kind == KIND_INCOME) " in" else "",
                                    fontSize = 14.sp, color = p.ink, fontWeight = FontWeight.SemiBold,
                                )
                                Text("${r.date} · ${r.subject}", fontSize = 12.sp, color = p.ink2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaperButton("Remove", { scope.launch { runCatching { GmailSync.remove(context, setOf(r.entryId)) }; tick++ } }, style = PaperButtonStyle.Ghost, small = true)
                                    PaperButton("Never this sender", {
                                        settings.ignore(r.domain)
                                        scope.launch { runCatching { GmailSync.remove(context, setOf(r.entryId)) }; tick++ }
                                    }, style = PaperButtonStyle.Ghost, small = true)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (ignored.isNotEmpty()) {
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Senders left out", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        ignored.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(d, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f))
                                PaperButton("Allow again", { settings.unignore(d); tick++ }, style = PaperButtonStyle.Ghost, small = true)
                            }
                        }
                    }
                }
            }
        }

        // ---- Google project ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your Google project", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        "No key is typed in here. Google recognises Kairos by its package name and signing key, from the Android client in your Google Cloud project, which must have the Gmail API switched on and the app published (not in Testing).",
                        fontSize = 12.5.sp, color = p.ink2,
                    )
                    Text("Package: $PACKAGE", fontSize = 12.5.sp, color = p.ink)
                    Text("SHA-1: $SHA1", fontSize = 12.5.sp, color = p.ink)
                }
            }
        }
    }
}
