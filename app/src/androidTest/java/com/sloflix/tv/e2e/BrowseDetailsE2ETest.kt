package com.sloflix.tv.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class BrowseDetailsE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        clearContinueWatching()
        composeRule.recreateActivityAndWait()
        composeRule.ensureLoggedIn()
        composeRule.waitForPosters()
    }

    @Test
    fun openFirstPoster_showsDetails_thenBackToHome() {
        composeRule.onFirstPoster().performClick()
        composeRule.waitForTag(TestTags.DetailsRoot, timeoutMs = 45_000).assertIsDisplayed()

        pressBack()
        composeRule.waitForTag(TestTags.HomeRoot, timeoutMs = 30_000).assertIsDisplayed()
    }
}
