package com.stark.sillytavern.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stark.sillytavern.ui.theme.AccentGreen
import com.stark.sillytavern.ui.theme.DarkInputBackground
import com.stark.sillytavern.ui.theme.DarkSurface
import com.stark.sillytavern.ui.theme.TextPrimary

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Type a message..."
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = {
                    Text(placeholder)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkInputBackground,
                    unfocusedContainerColor = DarkInputBackground,
                    disabledContainerColor = DarkInputBackground.copy(alpha = 0.5f),
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = DarkInputBackground,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (value.isNotBlank() && enabled) onSend() }
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = AccentGreen,
                    disabledContainerColor = AccentGreen.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}
