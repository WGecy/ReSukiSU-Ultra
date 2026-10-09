package org.bakasu.bakasuultra.domain.model

data class WebUiCommandResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
)
