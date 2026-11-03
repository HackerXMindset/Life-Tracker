package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifetracker.app.ui.theme.PaperSegmented

/**
 * Rules and place goals on one page: two tabs under one header. Rules do something when something happens;
 * goals are targets and limits from where you go. [startOnGoals] picks the tab it opens on.
 */
@Composable
fun RulesAndGoalsScreen(onBack: () -> Unit, startOnGoals: Boolean = false) {
    var tab by rememberSaveable { mutableStateOf(if (startOnGoals) 1 else 0) }
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsTop("Rules and goals", "Things done for you, and targets to aim for", onBack)
            PaperSegmented(listOf("Rules", "Goals"), tab, { tab = it })
        }
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            if (tab == 0) RulesScreen(onBack = onBack, embedded = true) else GoalsScreen(onBack = onBack, embedded = true)
        }
    }
}
