import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
}

spotless {
    lineEndings = LineEnding.UNIX

    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.compose.rules.get()}"))
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "ktlint_compose_modifier-missing-check" to "disabled",
                    "ktlint_compose_compositionlocal-allowlist" to "disabled",
                    "ktlint_compose_mutable-state-param-check" to "disabled",
                    "ktlint_compose_parameter-naming" to "disabled",
                    "ktlint_compose_modifier-naming" to "disabled",
                ),
            )
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
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
extra["isPrBuild"] = project.findProperty("IS_PR_BUILD")?.toString()?.toBoolean() ?: false
extra["defaultManagerPackageName"] = "org.bakasu.bakasuultra"
extra["managerPackageName"] = project.findProperty("KSU_PACKAGE_NAME")?.toString() ?: extra["defaultManagerPackageName"]
extra["defaultManagerAppName"] = if (extra["isPrBuild"] == true) "BakaSU Ultra PR" else "BakaSU Ultra"
extra["managerName"] = project.findProperty("KSU_NAME")?.toString() ?: extra["defaultManagerAppName"]

val isSpoofedBuild = project.findProperty("IS_SPOOFED_BUILD")?.toString()?.toBoolean() ?: false

fun getGitCommitCount(): Int {
    // 用 origin/main (与内核构建 fetch 后的 commit 数一致, 保证版本对齐)
    return providers.exec {
        commandLine("sh", "-c", "git rev-list --count origin/main 2>/dev/null || git rev-list --count HEAD")
    }.standardOutput.asText.get().trim().toInt()
}

fun getGitDescribe(): String {
    // 只匹配 v4.3.0 精确 tag: CI 构建 tag (ci-*) 与历史构建 tag (v4.3.0_*) 不污染 versionName
    val desc = providers.exec {
        commandLine("git", "describe", "--tags", "--match", "v4.3.0", "--always", "--abbrev=0")
    }.standardOutput.asText.get().trim()
    if (isSpoofedBuild) {
        return "$desc-spoofed"
    }
    return desc
}
