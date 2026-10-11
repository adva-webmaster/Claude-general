package de.ingvoss.fotosortierer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.center
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.SwipeDirection
import de.ingvoss.fotosortierer.core.SwipeMapping
import de.ingvoss.fotosortierer.ui.sort.SwipeCard
import de.ingvoss.fotosortierer.ui.sort.SwipeCardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumentierter Test der Wischgesten (läuft auf Gerät/Emulator: ./gradlew connectedDebugAndroidTest). */
@RunWith(AndroidJUnit4::class)
class SwipeCardTest {
    @get:Rule
    val rule = createComposeRule()

    private var decision: SwipeAction? = null
    private val state = SwipeCardState()

    private fun show(mapping: SwipeMapping = SwipeMapping.STANDARD) {
        rule.setContent {
            Box(Modifier.size(320.dp)) {
                SwipeCard(
                    state = state,
                    mapping = mapping,
                    haptics = false,
                    onDecision = { decision = it },
                    onTap = {},
                    modifier = Modifier.fillMaxSize().testTag("card")
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
    }

    @Test
    fun linksWischenBehaeltDasMedium() {
        show()
        rule.onNodeWithTag("card").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { decision != null }
        assertEquals(SwipeAction.BEHALTEN, decision)
    }

    @Test
    fun rechtsWischenLegtInDenPapierkorb() {
        show()
        rule.onNodeWithTag("card").performTouchInput { swipeRight() }
        rule.waitUntil(5_000) { decision != null }
        assertEquals(SwipeAction.PAPIERKORB, decision)
    }

    @Test
    fun obenWischenStelltZurueck() {
        show()
        rule.onNodeWithTag("card").performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { decision != null }
        assertEquals(SwipeAction.ZURUECKSTELLEN, decision)
    }

    @Test
    fun vertauschteBelegungWirdBeachtet() {
        show(SwipeMapping.STANDARD.with(SwipeDirection.LINKS, SwipeAction.PAPIERKORB))
        rule.onNodeWithTag("card").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { decision != null }
        assertEquals(SwipeAction.PAPIERKORB, decision)
    }

    @Test
    fun kurzesZiehenSpringtZurueckOhneEntscheidung() {
        show()
        rule.onNodeWithTag("card").performTouchInput {
            swipe(start = center, end = center + Offset(24f, 0f), durationMillis = 600)
        }
        rule.waitForIdle()
        assertNull(decision)
        assertEquals(0f, state.dragX, 0.5f)
        assertEquals(0f, state.dragY, 0.5f)
    }

    @Test
    fun abwaertsWischenLoestNichtsAus() {
        show()
        rule.onNodeWithTag("card").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 400f), durationMillis = 300)
        }
        rule.waitForIdle()
        assertNull(decision)
    }
}
