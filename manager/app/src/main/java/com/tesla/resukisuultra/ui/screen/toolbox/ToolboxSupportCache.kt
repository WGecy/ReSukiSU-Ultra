package com.tesla.resukisuultra.ui.screen.toolbox

/** 工具箱功能支持检测缓存 (进程级, 进入零 shell 开销) */
object ToolboxSupportCache {
    var netisolateChecked: Boolean = false
    var netisolate: Boolean = true
    var rootChecked: Boolean = false
    var root: Boolean = true
}
