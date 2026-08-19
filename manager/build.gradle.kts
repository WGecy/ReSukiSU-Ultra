plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 26
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidBuildToolsVersion"] = "36.1.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = 30000 + getGitCommitCount() + 800
extra["managerVersionName"] = getGitDescribe()

fun getGitCommitCount(): Int {
    // 用 origin/main (与内核构建 fetch 后的 commit 数一致, 保证版本对齐)
    return providers.exec {
        commandLine("sh", "-c", "git rev-list --count origin/main 2>/dev/null || git rev-list --count HEAD")
    }.standardOutput.asText.get().trim().toInt()
}

fun getGitDescribe(): String {
    // 只匹配 v4.3.0 精确 tag: CI 构建 tag (ci-*) 与历史构建 tag (v4.3.0_*) 不污染 versionName
    return providers.exec {
        commandLine("git", "describe", "--tags", "--match", "v4.3.0", "--always", "--abbrev=0")
    }.standardOutput.asText.get().trim()
}
