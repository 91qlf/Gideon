package app.gideon

import android.app.Application
import app.gideon.data.NewsRepository
import app.gideon.data.SourceCatalog
import app.gideon.data.UserStore
import app.gideon.work.DailyBriefWorker
import app.gideon.work.Notifications

class GideonApp : Application() {
    lateinit var repository: NewsRepository
        private set
    lateinit var store: UserStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = UserStore(this)
        val catalog = SourceCatalog.fromJson(assets.open("sources.json").bufferedReader().use { it.readText() })
        repository = NewsRepository(this, catalog)
        Notifications.createChannel(this)
        if (store.dailyBrief) DailyBriefWorker.schedule(this)
    }
}
