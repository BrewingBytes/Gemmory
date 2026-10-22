package com.brewingbytes.gemmory.streak

import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.glance.text.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brewingbytes.gemmory.MainActivity
import com.brewingbytes.gemmory.widget.StreakWidgetContent
import com.brewingbytes.gemmory.widget.StreakWidgetState
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget's layout in each state (#103): its number, its line and its one TalkBack description. Glance's test
 * environment needs Android's Bundle, so it runs on the device.
 */
@RunWith(AndroidJUnit4::class)
class StreakWidgetTest {
    private fun renders(state: StreakWidgetState, number: String) = runGlanceAppWidgetUnitTest {
        provideComposable {
            // Glance's Text in place of the TextViews, which need a device.
            StreakWidgetContent(state, actionStartActivity<MainActivity>()) { _, value, _ -> Text(value) }
        }
        onNode(hasText(number)).assertExists()
        onNode(hasText(state.line)).assertExists()
        onNode(hasContentDescription(state.description)).assertExists()
    }

    @Test
    fun notDoneToday() = renders(StreakWidgetState(6, lit = false), "6")

    @Test
    fun doneToday() = renders(StreakWidgetState(6, lit = true), "6")

    @Test
    fun startAStreak() = renders(StreakWidgetState(0, lit = false), "0")
}
