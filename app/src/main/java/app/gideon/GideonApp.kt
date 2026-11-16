package app.gideon

import android.app.Application
import app.gideon.data.NewsRepository
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
        repository = NewsRepository(this)
        Notifications.createChannel(this)
        if (store.dailyBrief) DailyBriefWorker.schedule(this)
    }
}
