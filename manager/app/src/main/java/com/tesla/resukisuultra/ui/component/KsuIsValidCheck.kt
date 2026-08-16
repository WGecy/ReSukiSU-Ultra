package com.tesla.resukisuultra.ui.component

import androidx.compose.runtime.Composable
import com.tesla.resukisuultra.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit
) {
    if (status.isValid)
        content()
}
