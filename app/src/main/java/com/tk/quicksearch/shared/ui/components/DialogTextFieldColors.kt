package com.tk.quicksearch.shared.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tk.quicksearch.shared.ui.theme.AppColors

@Composable
fun dialogTextFieldColors(
    unfocusedIndicatorColor: Color = MaterialTheme.colorScheme.outline,
): TextFieldColors =
    TextFieldDefaults.colors(
        focusedContainerColor = AppColors.Accent.copy(alpha = 0.14f),
        unfocusedContainerColor = AppColors.Accent.copy(alpha = 0.08f),
        disabledContainerColor = AppColors.Accent.copy(alpha = 0.05f),
        errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        focusedIndicatorColor = AppColors.Accent,
        unfocusedIndicatorColor = unfocusedIndicatorColor,
        focusedLabelColor = AppColors.Accent,
        cursorColor = AppColors.Accent,
    )
