package com.dero.juststudykorean.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.navigation.MoreDestination
import com.dero.juststudykorean.ui.navigation.Route
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.Tokens

private data class ScreenText(val title: String, val ko: String, val subtitle: String?, val subtitleKo: String?, val icon: Lucide)

// Texts from design/shell.md, "Pantallas provisionales".
private val Route.text: ScreenText
    get() = when (section) {
        MoreDestination.Grammar -> ScreenText("Grammar", "문법", "Explore Korean grammar with clear explanations and examples.", null, Lucide.Languages)
        MoreDestination.Progress -> ScreenText(
            "Progress", "진도",
            "Mastery = correct answers in your last 10 for each topic, out of 10. The numbers don't lie.",
            "숙달도 = 주제별 최근 10문제 중 맞힌 수. 숫자는 거짓말 안 해.", Lucide.TrendingUp,
        )
        MoreDestination.Calendar -> ScreenText(
            "Calendar", "달력", "Every day you studied, and every day you didn't.", "공부한 날도, 안 한 날도 전부.", Lucide.CalendarDays,
        )
        MoreDestination.Settings -> ScreenText("Settings", "설정", null, null, Lucide.Settings)
        null -> when (tab) {
            TopLevel.Dashboard -> ScreenText(
                "Dashboard", "대시보드", "No excuses. This is where you really stand.", "변명은 없어. 이게 진짜 네 실력이야.", Lucide.LayoutGrid,
            )
            TopLevel.Library -> ScreenText(
                "Library", "자료실", "Your study PDFs. No notes, no progress.", "공부용 PDF. 노트가 없으면 발전도 없어.", Lucide.BookText,
            )
            TopLevel.Flashcards -> ScreenText(
                "Flashcards", "단어 카드",
                "Words from your own notes. Review them before you forget them, again.",
                "네 노트에서 나온 단어. 또 잊어버리기 전에 복습해.", Lucide.Layers,
            )
            TopLevel.Quiz -> ScreenText(
                "Quiz", "퀴즈", "Questions built only from your own notes. No excuses if you fail.", "네 노트로만 만든 문제. 틀려도 변명은 없어.",
                Lucide.CircleHelp,
            )
            TopLevel.Teacher -> ScreenText(
                "Teacher", "선생님",
                "Your teacher answers from your own notes first. For anything else, AI is used.",
                "선생님은 먼저 네 노트로 대답해. 나머지는 AI를 사용해.", Lucide.GraduationCap,
            )
        }
    }

/** Phase 2 placeholder: the destination's top bar and the "Coming in phase 3" empty state with its icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(route: Route, onMore: () -> Unit, onBack: (() -> Unit)?) {
    val text = route.text
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar(text.title, text.ko, text.subtitle, text.subtitleKo, scroll, onMore = onMore, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space4)) {
            EmptyState(text.icon, "Coming in phase 3", ko = null, message = null)
        }
    }
}
