package com.dero.juststudykorean.ui.navigation

/** The destinations of the bottom bar (design/shell.md; Grammar since design/ajustes.md AJ-01). Games opens on top. */
enum class TopLevel { Dashboard, Library, Flashcards, Quiz, Teacher, Grammar }

/** What MoreSheet opens on top of the current destination, with a back arrow. No Progress screen, as in the web. */
enum class MoreDestination { Calendar, Settings }
