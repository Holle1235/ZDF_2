package de.zdf.app

import android.app.Application
import de.zdf.app.data.Repository
import de.zdf.app.update.UpdateWorker

class ZdfApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        runCatching { UpdateWorker.schedule(this) }
    }
}
