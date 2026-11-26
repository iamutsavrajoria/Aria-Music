// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aria.domain.AppSettings
import app.aria.music.BuildConfig
import app.aria.music.R
import app.aria.music.ui.common.AriaIcons
import app.aria.music.ui.common.LoadingState
import app.aria.music.ui.detail.DetailFrame
import kotlin.math.roundToInt

const val TAG_SETTINGS_HISTORY = "settings:history"
const val TAG_SETTINGS_RADIO = "settings:radio"
const val TAG_SETTINGS_SILENCE = "settings:silence"
const val TAG_SETTINGS_LOUDNESS = "settings:loudness"
const val TAG_SETTINGS_SLEEP = "settings:sleep"
const val TAG_SETTINGS_ABOUT = "settings:about"

data class SettingsActions(
    val onBack: () -> Unit,
    val onHistorySeconds: (Int) -> Unit,
    val onEndlessRadio: (Boolean) -> Unit,
    val onSkipSilence: (Boolean) -> Unit,
    val onNormalize: (Boolean) -> Unit,
    val onSleepDefault: (Int) -> Unit,
)

@Composable
fun SettingsContent(state: SettingsUiState, versionName: String, brandName: String, actions: SettingsActions, modifier: Modifier = Modifier) {
    DetailFrame(actions.onBack, modifier) {
        when (state) {
            SettingsUiState.Loading -> LoadingState()
            is SettingsUiState.Content -> SettingsBody(state.settings, versionName, brandName, actions)
        }
    }
}

@Composable
private fun SettingsBody(s: AppSettings, versionName: String, brandName: String, actions: SettingsActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.settings_title), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        SectionHeader(R.string.settings_playback)
        HistoryRow((s.historyThresholdMs / 1_000).toInt(), actions.onHistorySeconds)
        SwitchRow(TAG_SETTINGS_RADIO, R.string.settings_radio, R.string.settings_radio_body, s.endlessRadio, actions.onEndlessRadio)
        SwitchRow(TAG_SETTINGS_SILENCE, R.string.settings_silence, null, s.skipSilence, actions.onSkipSilence)
        SwitchRow(TAG_SETTINGS_LOUDNESS, R.string.settings_loudness, null, s.normalizeLoudness, actions.onNormalize)
        SleepRow(s.sleepTimerDefaultMinutes, actions.onSleepDefault)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader(R.string.settings_about)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(TAG_SETTINGS_ABOUT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(brandName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_about_non_affiliation, brandName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.settings_about_no_analytics), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title), Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
}

/** The whole row is the one switch (label and state read together); the [Switch] itself is only the picture. */
@Composable
private fun SwitchRow(tag: String, @StringRes label: Int, @StringRes body: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun HistoryRow(seconds: Int, onChange: (Int) -> Unit) {
    val title = stringResource(R.string.settings_history_title)
    // The thumb follows the finger at once; a write that is slow or normalized differently catches up when the stored value changes.
    var drag by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    val shown = snap(drag)
    val valueText = pluralStringResource(R.plurals.settings_history_value, shown, shown)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = drag,
            onValueChange = { raw -> drag = raw; val next = snap(raw); if (next != seconds) onChange(next) },
            // The semantics node is outside the sizing so its bounds are the 48dp target (the slider itself measures 44dp) and merges the slider into one node.
            modifier = Modifier.testTag(TAG_SETTINGS_HISTORY)
                .semantics(mergeDescendants = true) { contentDescription = title; stateDescription = valueText }
                .fillMaxWidth().heightIn(min = 48.dp),
            valueRange = SettingsChoices.HISTORY_MIN_S.toFloat()..SettingsChoices.HISTORY_MAX_S.toFloat(),
            steps = (SettingsChoices.HISTORY_MAX_S - SettingsChoices.HISTORY_MIN_S) / SettingsChoices.HISTORY_STEP_S - 1,
        )
    }
}

private fun snap(raw: Float): Int =
    ((raw / SettingsChoices.HISTORY_STEP_S).roundToInt() * SettingsChoices.HISTORY_STEP_S).coerceIn(SettingsChoices.HISTORY_MIN_S, SettingsChoices.HISTORY_MAX_S)

@Composable
private fun SleepRow(minutes: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 16.dp, vertical = 8.dp).testTag(TAG_SETTINGS_SLEEP),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_sleep_default), style = MaterialTheme.typography.bodyLarge)
                Text(pluralStringResource(R.plurals.np_sleep_in_minutes, minutes, minutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(AriaIcons.ExpandMore, contentDescription = null)
        }
        DropdownMenu(open, { open = false }) {
            for (m in SettingsChoices.SLEEP_MINUTES) {
                DropdownMenuItem(
                    text = { Text(pluralStringResource(R.plurals.np_sleep_in_minutes, m, m)) },
                    onClick = { open = false; onChange(m) },
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val actions = remember(viewModel, onBack) {
        SettingsActions(
            onBack = onBack, onHistorySeconds = viewModel::setHistorySeconds, onEndlessRadio = viewModel::setEndlessRadio,
            onSkipSilence = viewModel::setSkipSilence, onNormalize = viewModel::setNormalizeLoudness, onSleepDefault = viewModel::setSleepDefaultMinutes,
        )
    }
    SettingsContent(state, BuildConfig.VERSION_NAME, BuildConfig.BRAND_NAME, actions)
}
