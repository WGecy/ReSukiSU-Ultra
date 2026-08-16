package com.tesla.resukisuultra.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
