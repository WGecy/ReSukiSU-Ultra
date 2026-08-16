package com.tesla.resukisuultra.domain.usecase

import com.tesla.resukisuultra.domain.text.TextTransliterator

class TransliterateTextUseCase(private val transliterator: TextTransliterator) {
    operator fun invoke(value: String): String = transliterator.transliterate(value)
}
