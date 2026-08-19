package com.tesla.resukisuultra.ui.screen

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.tesla.resukisuultra.R
import com.tesla.resukisuultra.ui.screen.main.HomePage
import com.tesla.resukisuultra.ui.screen.main.KpmPage
import com.tesla.resukisuultra.ui.screen.main.ModulePage
import com.tesla.resukisuultra.ui.screen.main.SettingsPage
import com.tesla.resukisuultra.ui.screen.main.SuperUserPage

enum class BottomBarDestination(
    val direction: @Composable (bottomPadding: Dp) -> Unit,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
) {
    Home(
        { bottomPadding -> HomePage(bottomPadding) },
        R.string.home,
        Icons.TwoTone.Home,
        Icons.TwoTone.Home,
        false
    ),
    SuperUser(
        { bottomPadding -> SuperUserPage(bottomPadding) },
        R.string.superuser,
        Icons.TwoTone.AdminPanelSettings,
        Icons.TwoTone.AdminPanelSettings,
        true
    ),
    Module(
        { bottomPadding -> ModulePage(bottomPadding) },
        R.string.module,
        Icons.TwoTone.Extension,
        Icons.TwoTone.Extension,
        true
    ),
    Kpm(
        { bottomPadding -> KpmPage(bottomPadding) },
        R.string.kpm,
        Icons.TwoTone.Build,
        Icons.TwoTone.Build,
        true
    ),
    Settings(
        { bottomPadding -> SettingsPage(bottomPadding) },
        R.string.settings,
        Icons.TwoTone.Settings,
        Icons.TwoTone.Settings,
        false
    );

    companion object {
        fun getPages(isKsuValid: Boolean, kpmSupported: Boolean = false): List<BottomBarDestination> {
            return if (isKsuValid) {
                // 全功能管理器 (KPM 需内核适配, 不支持则隐藏)
                BottomBarDestination.entries.filter {
                    it != Kpm || kpmSupported
                }
            } else {
                BottomBarDestination.entries.filter {
                    !it.rootRequired
                }
            }
        }
    }
}
