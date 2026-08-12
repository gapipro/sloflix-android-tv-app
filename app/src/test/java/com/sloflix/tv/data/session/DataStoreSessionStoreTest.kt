package com.sloflix.tv.data.session

import androidx.test.core.app.ApplicationProvider
import com.sloflix.tv.domain.session.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreSessionStoreTest {
    @Test
    fun roundTripAndClear() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreSessionStore(context)
        assertNull(store.get())
        store.set(Session(accessToken = "tok", cookieHeader = "a=b"))
        assertEquals(Session("tok", "a=b"), store.get())
        store.clear()
        assertNull(store.get())
    }
}
