package org.bakasu.bakasuultra.domain.model

data class ModuleUpdateMetadata(
    val zipUrl: String,
    val version: String,
    val changelog: String,
)
