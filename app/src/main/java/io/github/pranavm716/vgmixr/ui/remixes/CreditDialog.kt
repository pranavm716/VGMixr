package io.github.pranavm716.vgmixr.ui.remixes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.theme.VGMixrColors

/**
 * A remix's credit: one choice per name, its own channel first (which clears the credit), the
 * current one marked. A tap on a choice saves it. New name opens a text field, saved with Add or the
 * keyboard's Done; Add is off while it is blank.
 */
@Composable
fun CreditDialog(
    dialog: CreditDialogUi,
    onPick: (String?) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var typing by rememberSaveable(dialog.trackId) { mutableStateOf(false) }
    var typed by rememberSaveable(dialog.trackId) { mutableStateOf("") }
    val add = { if (typed.isNotBlank()) onAdd(typed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VGMixrColors.dialog,
        title = {
            Column {
                Text(stringResource(R.string.credit))
                Text(
                    dialog.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                Choice(
                    dialog.channel,
                    stringResource(R.string.credit_channel),
                    dialog.selected == null
                ) { onPick(null) }
                dialog.choices.forEach { name ->
                    Choice(name, null, dialog.selected == name) { onPick(name) }
                }
                if (typing) {
                    NewName(typed, onChange = { typed = it }, onDone = add)
                } else {
                    NewNameButton { typing = true }
                }
            }
        },
        confirmButton = {
            if (typing) {
                TextButton(onClick = add, enabled = typed.isNotBlank()) {
                    Text(stringResource(R.string.credit_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun Choice(name: String, label: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The row handles the tap, so the button only shows the choice.
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NewNameButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        }
        Text(stringResource(R.string.credit_new_name), Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun NewName(typed: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = typed,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.credit_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .focusRequester(focus)
    )
}
