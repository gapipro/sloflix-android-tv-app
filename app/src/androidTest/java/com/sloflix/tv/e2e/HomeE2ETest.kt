package com.sloflix.tv.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
class HomeE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun ensureHome() {
        composeRule.ensureLoggedIn()
    }

    @Test
    fun homeShowsFilmiChipAndFilters() {
        composeRule.onNodeWithTag(TestTags.HomeFilmi).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.HomeSerije).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.HomeFilters).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.HomeProfile).assertIsDisplayed()
    }

    @Test
    fun openAndCloseFiltersPanel() {
        composeRule.onNodeWithTag(TestTags.HomeFilters).performClick()
        composeRule.waitForTag(TestTags.FilterPanel).assertIsDisplayed()
        composeRule.waitForTag(TestTags.FilterClose).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(TestTags.FilterPanel).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun openProfileMenu_showsSettingsAndSignOut() {
        composeRule.onNodeWithTag(TestTags.HomeProfile).performClick()
        composeRule.waitForTag(TestTags.ProfileMenu).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ProfileSettings).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ProfileSignOut).assertIsDisplayed()
    }

    @Test
    fun settings_canSwitchLanguageToEnglishAndBack() {
        composeRule.onNodeWithTag(TestTags.HomeProfile).performClick()
        composeRule.waitForTag(TestTags.ProfileSettings).performClick()
        composeRule.waitForTag(TestTags.SettingsPanel).assertIsDisplayed()

        composeRule.waitForTag(TestTags.SettingsLanguageEn).performClick()
        composeRule.waitForTag(TestTags.SettingsClose).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(TestTags.SettingsPanel).fetchSemanticsNodes().isEmpty()
        }

        // Filters label becomes English "Filters"
        composeRule.onNodeWithTag(TestTags.HomeFilters).assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.HomeProfile).performClick()
        composeRule.waitForTag(TestTags.ProfileSettings).performClick()
        composeRule.waitForTag(TestTags.SettingsLanguageSl).performClick()
        composeRule.waitForTag(TestTags.SettingsClose).performClick()
    }

    @Test
    fun filmiAndSerijeChips_areClickable() {
        composeRule.onNodeWithTag(TestTags.HomeFilmi).performClick()
        composeRule.onNodeWithTag(TestTags.HomeSerije).performClick()
        composeRule.onNodeWithTag(TestTags.HomeVse).performClick()
        composeRule.onNodeWithTag(TestTags.HomeRoot).assertIsDisplayed()
    }
}
