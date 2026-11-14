package com.dero.juststudykorean.ui.games

import com.dero.juststudykorean.korean.games.GameType

/** A game's name and instruction, in English with its Korean (design/games-minigames.md). */
data class GameText(val name: String, val ko: String, val instruction: String, val instructionKo: String)

val GAME_TEXTS: Map<GameType, GameText> = mapOf(
    GameType.Hangman to GameText("Hangul Hangman", "행맨", "Find the word before the lanterns go out.", "등불이 꺼지기 전에 단어를 찾으세요."),
    GameType.WordMatch to GameText("Word Match", "짝 맞추기", "Match each word with its meaning.", "뜻과 짝을 맞추세요."),
    GameType.SentenceBuilder to GameText("Sentence Builder", "문장 만들기", "Build the sentence as in your notes.", "노트에 있는 대로 문장을 만드세요."),
    GameType.MemoryFlip to GameText("Memory Flip", "카드 뒤집기", "Find the pairs.", "짝을 찾으세요."),
    GameType.WordScramble to GameText("Word Scramble", "음절 섞기", "Put the syllables in order.", "음절을 순서대로 놓으세요."),
    GameType.ListenGuess to GameText("Listen & Guess", "듣고 맞히기", "Tap the card you hear.", "들리는 카드를 누르세요."),
    GameType.HangulBuilder to GameText("Hangul Builder", "글자 만들기", "Build the missing block.", "빠진 글자를 만드세요."),
    GameType.WordSort to GameText("Word Sort", "분류하기", "Sort each word into its group.", "단어를 알맞은 그룹에 넣으세요."),
    GameType.GrammarFixer to GameText("Grammar Fixer", "문법 고치기", "One particle is wrong. Tap the word.", "조사가 하나 틀렸어요. 단어를 누르세요."),
    GameType.WordRecall to GameText("Word Recall", "순서 기억하기", "Remember the order.", "순서를 기억하세요."),
    GameType.HangulTyping to GameText("Hangul Typing", "한글 타자", "Type this word.", "이 단어를 쓰세요."),
    GameType.WordPath to GameText("Korean Word Path", "단어 길", "Trace the word through the grid.", "글자를 이어 단어를 만드세요."),
    GameType.SoundDetective to GameText("Sound Detective", "소리 탐정", "Same word or different?", "같은 단어예요, 다른 단어예요?"),
)
