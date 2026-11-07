package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.max2idea.android.limbo.ui.exp.theme.expCardColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Dialogs in the NPatch shape, replacing the platform `AlertDialog`s.
 *
 * §32/§33 of the gap list call this the single biggest "this does not look like
 * NPatch" moment in the screen: every HyperOS-style surface elsewhere is a soft
 * squircle card, and then tapping a row threw a Material dialog with square
 * corners, an all-caps button and a hard divider on top of it. These two
 * composables rebuild the confirmation and the text prompt out of the same
 * primitives as the rest of the page — a Miuix [Card], 17sp bold title, 14sp
 * summary, and borderless text buttons — so opening one no longer breaks the
 * illusion.
 *
 * `androidx.compose.ui.window.Dialog` is used directly instead of Miuix's own
 * `OverlayDialog`: that one takes sixteen parameters, several of them inline
 * value classes, and the call is easy to get subtly wrong.
 */
@Composable
fun ExpConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ExpDialogFrame(onDismiss = onDismiss) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 8.dp),
        )
        ExpDialogButtons(
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/**
 * The "new machine" prompt.
 *
 * [error] is non-null when the last confirm attempt failed validation; the
 * message is shown inline under the field instead of as a toast, so the prompt
 * stays open and the user can fix the name where they typed it.
 */
@Composable
fun ExpPromptDialog(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ExpDialogFrame(onDismiss = onDismiss) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(
                    color = MiuixTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) {
            Text(
                text = error,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        ExpDialogButtons(
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun ExpDialogFrame(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = expCardColors(),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ExpDialogButtons(
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            text = dismissLabel,
            onClick = onDismiss,
            minWidth = 72.dp,
        )
        Button(
            onClick = onConfirm,
            minWidth = 88.dp,
        ) {
            Text(text = confirmLabel, fontSize = 15.sp)
        }
    }
}
