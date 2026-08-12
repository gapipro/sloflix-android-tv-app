package com.sloflix.tv.data.api

import com.sloflix.tv.domain.session.Session
import java.util.concurrent.atomic.AtomicReference

interface SessionProvider {
    fun session(): Session?
}

class MutableSessionProvider : SessionProvider {
    private val current = AtomicReference<Session?>()

    override fun session(): Session? = current.get()

    fun update(session: Session?) {
        current.set(session)
    }
}
