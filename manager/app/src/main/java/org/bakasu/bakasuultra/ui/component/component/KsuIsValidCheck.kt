package org.bakasu.bakasuultra.ui.component

import androidx.compose.runtime.Composable
import org.bakasu.bakasuultra.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit
) {
    if (status.isFullFeatured)
        content()
}
