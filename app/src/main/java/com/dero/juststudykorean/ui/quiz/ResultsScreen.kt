package com.dero.juststudykorean.ui.quiz

import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.quiz.ResultRow
import com.dero.juststudykorean.ui.components.AiMark
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun ResultsScreen(key: String, onClose: () -> Unit, onNewQuiz: () -> Unit, vm: ResultsViewModel = hiltViewModel()) {
    LaunchedEffect(key) { vm.open(key) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val aiFailed by vm.aiFailed.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    ResultsContent(rows, onClose, onNewQuiz, aiFailed)
}

/** Results (design/quiz.md D): the score, the sentence for it and each answer. */
@Composable
fun ResultsContent(rows: List<ResultRow>?, onClose: () -> Unit, onNewQuiz: () -> Unit, aiFailed: Int = 0) {
    val c = JustStudyTheme.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        SmallBar(Lucide.X, "Close results", "Results", null, onClose)
        Box(Modifier.weight(1f).padding(horizontal = Tokens.Space4)) {
            if (rows == null) LoadingState("Loading…") else Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .border(Tokens.Border, c.line, MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space5),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
            ) {
                val right = rows.count { it.correct }
                Text("$right / ${rows.size} correct", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                val line = when {
                    right == rows.size -> "Perfect. Now do it again tomorrow."
                    right * 2 >= rows.size -> "Not bad, but not enough. Go again."
                    else -> "That was weak. Go again."
                }
                Text("$line Your answers were saved and count towards your progress.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                val byAi = rows.count { it.question.id < 0 } // R1-07 (design/quiz.md A.4): the model's questions live in memory only
                if (byAi > 0) Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space2), verticalAlignment = Alignment.CenterVertically) {
                    AiMark(short = true)
                    Text("$byAi of these ${if (byAi == 1) "was" else "were"} written by AI and ${if (byAi == 1) "isn't" else "aren't"} saved in your history.", fontSize = 12.sp, color = c.muted)
                }
                if (aiFailed > 0) Text("$aiFailed AI ${if (aiFailed == 1) "question" else "questions"} couldn't be written this time.", fontSize = 12.sp, color = c.muted) // A.3
                Column(Modifier.fillMaxWidth().border(Tokens.Border, c.line, MaterialTheme.shapes.medium)) {
                    rows.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(thickness = Tokens.Border, color = c.line)
                        Row(Modifier.fillMaxWidth().padding(Tokens.Space4), horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                            LucideIcon(if (r.correct) Lucide.Check else Lucide.X, contentDescription = if (r.correct) "Right" else "Wrong", size = 20.dp, tint = if (r.correct) c.correct else c.wrong, modifier = Modifier.padding(top = 2.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                                Text("${i + 1}. ${r.question.prompt}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                                Text(buildAnnotatedString {
                                    withStyle(SpanStyle(color = c.muted)) { append("Your answer: ") }
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (r.correct) c.correct else c.wrong)) { append(r.given) }
                                }, style = MaterialTheme.typography.bodyMedium)
                                if (!r.correct) Text(buildAnnotatedString {
                                    withStyle(SpanStyle(color = c.muted)) { append("Correct answer: ") }
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(r.question.correctAnswer) }
                                }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(Tokens.Space4)) { StickerButton("New quiz", onNewQuiz, Modifier.fillMaxWidth(), ko = "새 퀴즈") }
    }
}

