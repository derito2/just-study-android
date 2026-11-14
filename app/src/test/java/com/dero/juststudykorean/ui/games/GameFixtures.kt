package com.dero.juststudykorean.ui.games

import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.ItemKind
import com.dero.juststudykorean.korean.norm

/** Invented words and sentences for the games' screen tests (never the owner's notes). */
object GameFixtures {
    fun word(k: String, m: String) = GameItem("w:${norm(k)}", ItemKind.WORD, k, listOf(k), m, k.map { it.toString() }, 1, "Unit 3 · At school.pdf", 4)
    fun sentence(k: String, m: String) = GameItem("s:${norm(k)}", ItemKind.SENTENCE, k, listOf(k), m, k.filter { it in '가'..'힣' }.map { it.toString() }, 1, "Unit 3 · At school.pdf", 5)

    val apple = word("사과", "Apple")
    val milk = word("우유", "Milk")
    val school = word("학교", "School")
    val library = word("도서관", "Library")
    val friend = word("친구", "Friend")
    val cat = word("고양이", "Cat")
    val water = word("물", "Water")
    val book = word("책", "Book")
    val WORDS = listOf(apple, milk, school, library, friend, cat, water, book)
}
