package com.sloflix.tv.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.sloflix.tv.MainActivity
import com.sloflix.tv.ui.TestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SeriesDetailsE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        clearContinueWatching()
        composeRule.recreateActivityAndWait()
        composeRule.ensureLoggedIn()
        composeRule.onNodeWithTag(TestTags.HomeSerije).performClick()
        composeRule.waitForPosters()
    }

    @Test
    fun openSeries_showsDetailsSeasonsOrEpisodes() {
        composeRule.onFirstPoster().performClick()
        composeRule.waitForTag(TestTags.DetailsRoot, timeoutMs = 45_000).assertIsDisplayed()

        composeRule.waitUntil(45_000) {
            composeRule.onAllNodesWithTag(TestTags.DetailsSeasonChip).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(TestTags.DetailsEpisodes).fetchSemanticsNodes().isNotEmpty()
        }

        val hasEpisodes = composeRule.onAllNodesWithTag(TestTags.DetailsEpisodes)
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (hasEpisodes) {
            composeRule.onNodeWithTag(TestTags.DetailsEpisodes)
                .onChildren()
                .onFirst()
                .performClick()
            composeRule.waitForTag(TestTags.DetailsRoot, timeoutMs = 45_000).assertIsDisplayed()
            composeRule.waitUntil(45_000) {
                composeRule.onAllNodesWithTag(TestTags.DetailsPlay).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithTag(TestTags.DetailsWebViewPlay).fetchSemanticsNodes().isNotEmpty()
            }
            val playTag = if (
                composeRule.onAllNodesWithTag(TestTags.DetailsPlay).fetchSemanticsNodes().isNotEmpty()
            ) {
                TestTags.DetailsPlay
            } else {
                TestTags.DetailsWebViewPlay
            }
            composeRule.onNodeWithTag(playTag).performClick()
            composeRule.waitForTag(TestTags.PlayerRoot, timeoutMs = 120_000).assertIsDisplayed()
            pressBack()
            composeRule.waitForTag(TestTags.DetailsRoot, timeoutMs = 30_000)
            pressBack()
            composeRule.waitForTag(TestTags.DetailsRoot, timeoutMs = 30_000)
        }

        pressBack()
        composeRule.waitForTag(TestTags.HomeRoot, timeoutMs = 30_000).assertIsDisplayed()
    }
}
