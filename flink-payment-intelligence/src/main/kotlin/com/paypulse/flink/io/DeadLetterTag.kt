package com.paypulse.flink.io

import com.paypulse.flink.model.DeadLetter
import org.apache.flink.util.OutputTag

/**
 * Side output для записей, не прошедших парсинг. Анонимный подкласс нужен Flink,
 * чтобы сохранить generic-тип `DeadLetter` (стирание типов иначе теряет TypeInformation).
 */
val DEAD_LETTER_TAG: OutputTag<DeadLetter> = object : OutputTag<DeadLetter>("dead-letter") {}
