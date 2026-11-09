package io.github.twonet.selfish

import android.app.Application

/** Builds the one [AppGraph] for the process (DESIGN §3.4). */
class SelfishApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.store.start()
    }
}
