package com.lifetracker.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.GmailLog
import com.lifetracker.app.data.GmailSettings
import com.lifetracker.app.data.KIND_INCOME
import com.lifetracker.app.data.MoneySettings
import com.lifetracker.app.data.SmsSettings
import com.lifetracker.app.data.SmsSync
import com.lifetracker.app.data.SmsSyncWorker
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import kotlinx.coroutines.launch

/** Settings → SMS purchases: allow reading texts, how far back, and what was added. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    val settings = remember { SmsSettings(context) }
    val log = remember { GmailLog(context, SmsSync.LOG_FILE) }
    val currency = remember { MoneySettings(context).currency }
    var tick by remember { mutableIntStateOf(0) }
    val allowed = remember(tick) { SmsSync.hasAccess(context) }
    val recent = remember(tick) { log.recent().sortedByDescending { it.atMs } }
    val last = remember(tick) { settings.lastLine }
    val moneyOn = remember(tick) { Features.isOn(context, Feature.MONEY) }
    var reading by remember { mutableStateOf(false) }
    var months by remember { mutableIntStateOf(settings.months) }
    var income by remember { mutableStateOf(settings.includeIncome) }

    // Coming back from Android's own settings page, check the permission again.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun readNow() {
        reading = true
        scope.launch {
            runCatching { SmsSync.run(context) }
            reading = false
            tick++
        }
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (granted) {
            SmsSyncWorker.schedule(context)
            readNow()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("SMS purchases", "Money from bank and UPI texts", onBack) }
        if (!moneyOn) {
            item {
                Text(
                    "The Money feature is switched off, so nothing will be added to the Money tab until you switch it on (Settings → Features).",
                    fontSize = 13.sp, color = p.bad, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item {
            Text(
                "Kairos reads the texts your bank, card and UPI apps send (\"Rs.471 debited ... to ZOMATO\") and adds each payment to the Money tab with the shop, the amount and the day. " +
                    "One-time codes, offers, failed payments, upcoming bills and card bill payments are left out. Everything is read and kept on this phone only. " +
                    "If a receipt from Gmail is there for the same payment, only one entry is added, and it keeps the shop's name from the receipt.",
                fontSize = 13.sp, color = p.ink2,
            )
        }

        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reading your texts", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    if (allowed) {
                        Text("Allowed.", fontSize = 13.sp, color = p.ink)
                    } else {
                        Text("Not allowed yet. Kairos needs Android's SMS permission to read your texts. It cannot send or delete any.", fontSize = 13.sp, color = p.ink2)
                        PaperButton("Allow reading texts", { ask.launch(Manifest.permission.READ_SMS) }, small = true)
                        Text(
                            "If Android does not show the question, or says it is restricted: open App info for Kairos, press the ⋮ menu and choose \"Allow restricted settings\" (it only appears for an app installed from a file), then Permissions → SMS → Allow.",
                            fontSize = 12.sp, color = p.ink2,
                        )
                        PaperButton("Open app info", { CallsCollector.openAppSettings(context) }, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
        }

        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("How far back to read", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GmailSettings.MONTH_CHOICES.forEach { (value, label) ->
                            PaperPill(label, months == value, { months = value; settings.months = value; tick++ })
                        }
                    }
                    Text("Money received", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperPill("Refunds only", !income, { income = false; settings.includeIncome = false })
                        PaperPill("Everything credited", income, { income = true; settings.includeIncome = true })
                    }
                    Text(
                        "Refunds always count. With \"Everything credited\", salary and money people send you are added as income too (new texts, and older ones not read before).",
                        fontSize = 12.sp, color = p.ink2,
                    )
                    Text("New texts are read every 15 minutes or so and when the app opens. Changing how far back reads the older ones next time.", fontSize = 12.sp, color = p.ink2)
                    PaperButton(if (reading) "Reading…" else "Read now", { readNow() }, small = true, enabled = allowed && !reading)
                    if (last.isNotEmpty()) Text(last, fontSize = 12.5.sp, color = p.ink)
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Recently added", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        Text("Taken off here, an entry stays off.", fontSize = 12.5.sp, color = p.ink2)
                        recent.take(25).forEach { r ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "${r.merchant} · $currency%.2f".format(r.amount) + if (r.kind == KIND_INCOME) " in" else "",
                                    fontSize = 14.sp, color = p.ink, fontWeight = FontWeight.SemiBold,
                                )
                                Text("${r.date} · ${r.subject}", fontSize = 12.sp, color = p.ink2)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaperButton("Remove", { scope.launch { runCatching { SmsSync.remove(context, setOf(r.entryId)) }; tick++ } }, style = PaperButtonStyle.Ghost, small = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
