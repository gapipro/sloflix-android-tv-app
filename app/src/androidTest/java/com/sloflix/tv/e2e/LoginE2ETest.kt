package com.sloflix.tv.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
class LoginE2ETest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(ClearSessionRule())
        .around(composeRule)

    @Before
    fun settle() {
        composeRule.waitUntilHomeOrLogin()
    }

    @Test
    fun invalidCredentials_showError() {
        composeRule.waitForTag(TestTags.LoginUsername).performClick()
        composeRule.onNodeWithTag(TestTags.LoginUsername).performTextClearance()
        composeRule.onNodeWithTag(TestTags.LoginUsername).performTextInput("not-a-real-user")

        composeRule.waitForTag(TestTags.LoginPassword).performClick()
        composeRule.onNodeWithTag(TestTags.LoginPassword).performTextClearance()
        composeRule.onNodeWithTag(TestTags.LoginPassword).performTextInput("wrong-password")

        composeRule.waitForTag(TestTags.LoginSubmit).performClick()
        composeRule.waitForTag(TestTags.LoginError, timeoutMs = 30_000).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.LoginSubmit).assertIsDisplayed()
    }

    @Test
    fun validCredentials_navigateToHome() {
        E2ECredentials.assumePresent()
        composeRule.loginWithCredentials(E2ECredentials.username!!, E2ECredentials.password!!)
        composeRule.onNodeWithTag(TestTags.HomeRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.HomeFilmi).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.HomeProfile).assertIsDisplayed()
    }
}
