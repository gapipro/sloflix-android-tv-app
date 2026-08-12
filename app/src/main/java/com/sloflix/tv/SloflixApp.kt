package com.sloflix.tv

import android.app.Application
import com.sloflix.tv.di.AppContainer

class SloflixApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
