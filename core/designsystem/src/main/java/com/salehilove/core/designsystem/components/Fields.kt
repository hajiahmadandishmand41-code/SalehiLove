package com.salehilove.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Labelled text field used across all editors. RTL-friendly (the whole app is
 * laid out RTL) and shows optional validation errors under the field.
 */
@Composable
fun PersianTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
) {
    val msg = errorMessage ?: supportingText
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(12.dp),
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
            supportingText = if (msg != null) {
                { Text(msg) }
            } else {
                null
            },
        )
    }
}

/** PIN input (digits only, masked). */
@Composable
fun PinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    PersianTextField(
        label = label,
        value = value,
        onValueChange = { if (it.length <= 8 && it.all { c -> c in '0'..'9' }) onValueChange(it) },
        modifier = modifier,
        errorMessage = errorMessage,
        keyboardType = KeyboardType.NumberPassword,
        visualTransformation = PasswordVisualTransformation(),
    )
}
