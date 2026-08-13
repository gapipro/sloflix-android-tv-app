package com.sloflix.tv.e2e

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sloflix.tv.data.playback.DataStoreContinueWatchingStore
import com.sloflix.tv.data.session.DataStoreSessionStore
import com.sloflix.tv.domain.model.ContinueWatchingEntry
import com.sloflix.tv.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule

fun clearAppSession() {
    val context: Context = ApplicationProvider.getApplicationContext()
    runBlocking {
        DataStoreSessionStore(context).clear()
    }
}

fun clearContinueWatching() {
    val context: Context = ApplicationProvider.getApplicationContext()
    runBlocking {
        val store = DataStoreContinueWatchingStore(context)
        store.all().forEach { store.remove(it.titleId) }
    }
}

fun seedContinueWatching(
    titleId: String = "e2e-resume-title",
    name: String = "E2E Resume Title",
    positionMs: Long = ContinueWatchingEntry.MinResumePositionMs,
    durationMs: Long = 7_200_000L,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    runBlocking {
        DataStoreContinueWatchingStore(context).upsert(
            ContinueWatchingEntry(
                titleId = titleId,
                name = name,
                posterUrl = null,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }
}

fun hasTestTagStartingWith(prefix: String): SemanticsMatcher =
    SemanticsMatcher("TestTag starts with '$prefix'") { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        tag != null && tag.startsWith(prefix)
    }

fun AndroidComposeTestRule<*, *>.waitForTag(
    tag: String,
    timeoutMs: Long = 30_000,
): SemanticsNodeInteraction {
    waitUntil(timeoutMs) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    return onNodeWithTag(tag)
}

fun AndroidComposeTestRule<*, *>.waitUntilHomeOrLogin(timeoutMs: Long = 45_000) {
    waitUntil(timeoutMs) {
        onAllNodesWithTag(TestTags.HomeRoot).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag(TestTags.LoginSubmit).fetchSemanticsNodes().isNotEmpty()
    }
}

fun AndroidComposeTestRule<*, *>.waitForPosters(timeoutMs: Long = 60_000) {
    waitUntil(timeoutMs) {
        onAllNodes(hasTestTagStartingWith(TestTags.PosterPrefix))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
}

fun AndroidComposeTestRule<*, *>.onFirstPoster(): SemanticsNodeInteraction =
    onAllNodes(hasTestTagStartingWith(TestTags.PosterPrefix)).onFirst()

fun AndroidComposeTestRule<*, *>.ensureLoggedOut() {
    waitUntilHomeOrLogin()
    if (onAllNodesWithTag(TestTags.HomeProfile).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag(TestTags.HomeProfile).performClick()
        waitForTag(TestTags.ProfileSignOut).performClick()
        waitForTag(TestTags.LoginSubmit)
    } else {
        waitForTag(TestTags.LoginSubmit)
    }
}

fun AndroidComposeTestRule<*, *>.ensureLoggedIn() {
    E2ECredentials.assumePresent()
    waitUntilHomeOrLogin()
    if (onAllNodesWithTag(TestTags.LoginSubmit).fetchSemanticsNodes().isNotEmpty()) {
        loginWithCredentials(E2ECredentials.username!!, E2ECredentials.password!!)
    }
    waitForTag(TestTags.HomeRoot)
}

fun AndroidComposeTestRule<*, *>.loginWithCredentials(username: String, password: String) {
    waitForTag(TestTags.LoginUsername).performClick()
    // After click-to-edit, the same tagged node should accept text.
    onNodeWithTag(TestTags.LoginUsername).performTextClearance()
    onNodeWithTag(TestTags.LoginUsername).performTextInput(username)

    waitForTag(TestTags.LoginPassword).performClick()
    onNodeWithTag(TestTags.LoginPassword).performTextClearance()
    onNodeWithTag(TestTags.LoginPassword).performTextInput(password)

    waitForTag(TestTags.LoginSubmit).performClick()
    waitForTag(TestTags.HomeRoot, timeoutMs = 60_000)
}

fun AndroidComposeTestRule<*, *>.recreateActivityAndWait() {
    activity.runOnUiThread { activity.recreate() }
    waitUntilHomeOrLogin(timeoutMs = 60_000)
}

fun uiDevice(): UiDevice =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

fun pressDpad(keyCode: Int, times: Int = 1) {
    val device = uiDevice()
    repeat(times) {
        device.pressKeyCode(keyCode)
        Thread.sleep(200)
    }
}

fun pressCenter() = pressDpad(KeyEvent.KEYCODE_DPAD_CENTER)
fun pressBack() = uiDevice().pressBack()
fun pressLeft(times: Int = 1) = pressDpad(KeyEvent.KEYCODE_DPAD_LEFT, times)
fun pressRight(times: Int = 1) = pressDpad(KeyEvent.KEYCODE_DPAD_RIGHT, times)

/** Clears session before the activity rule launches (use as outer @Rule Order). */
class ClearSessionRule : TestRule {
    override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description) =
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                clearAppSession()
                base.evaluate()
            }
        }
}

/** Seeds a resume entry before the activity rule launches. */
class SeedContinueWatchingRule(
    private val titleId: String = "e2e-resume-title",
    private val name: String = "E2E Resume Title",
) : TestRule {
    override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description) =
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                seedContinueWatching(titleId = titleId, name = name)
                base.evaluate()
            }
        }
}
