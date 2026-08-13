package com.sloflix.tv.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.sloflix.tv.MainActivity
import com.sloflix.tv.ui.TestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ResumeWatchingE2ETest {
    private val resumeTitleId = "e2e-resume-title"
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(SeedContinueWatchingRule(titleId = resumeTitleId, name = "E2E Resume Title"))
        .around(composeRule)

    @Before
    fun setup() {
        composeRule.ensureLoggedIn()
        // Home may have loaded before the seed was visible to an already-running VM in rare cases;
        // recreate ensures the continue-watching row is built from DataStore.
        if (composeRule.onAllNodesWithTag(TestTags.HomeContinueWatching).fetchSemanticsNodes().isEmpty()) {
            composeRule.recreateActivityAndWait()
            composeRule.ensureLoggedIn()
        }
        composeRule.waitForTag(TestTags.HomeContinueWatching, timeoutMs = 60_000)
    }

    @Test
    fun seededEntry_showsContinueWatchingRow() {
        composeRule.onNodeWithTag(TestTags.HomeContinueWatching).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.poster(resumeTitleId)).assertIsDisplayed()
    }

    @Test
    fun longPress_showsRemoveDialog_cancelDismisses() {
        composeRule.onNodeWithTag(TestTags.poster(resumeTitleId))
            .performTouchInput { longClick() }

        composeRule.waitForTag(TestTags.ContinueWatchingRemoveConfirm).assertIsDisplayed()
        composeRule.waitForTag(TestTags.ContinueWatchingRemoveCancel).performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(TestTags.ContinueWatchingRemoveConfirm)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithTag(TestTags.HomeContinueWatching).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.poster(resumeTitleId)).assertIsDisplayed()
    }

    @Test
    fun longPress_confirmRemovesEntry() {
        composeRule.onNodeWithTag(TestTags.poster(resumeTitleId))
            .performTouchInput { longClick() }

        composeRule.waitForTag(TestTags.ContinueWatchingRemoveConfirm).performClick()

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(TestTags.HomeContinueWatching)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}
