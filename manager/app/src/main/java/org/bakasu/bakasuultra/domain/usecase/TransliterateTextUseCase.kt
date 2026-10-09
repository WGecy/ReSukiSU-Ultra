package org.bakasu.bakasuultra.domain.usecase

import org.bakasu.bakasuultra.domain.text.TextTransliterator

class TransliterateTextUseCase(private val transliterator: TextTransliterator) {
    operator fun invoke(value: String): String = transliterator.transliterate(value)
}
