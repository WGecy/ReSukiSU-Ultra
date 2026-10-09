package org.bakasu.bakasuultra.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
