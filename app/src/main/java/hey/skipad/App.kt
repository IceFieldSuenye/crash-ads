package hey.skipad

import android.app.Application
import hey.skipad.data.LogStore
import hey.skipad.data.SettingsRepository
import hey.skipad.data.StatsRepository
import hey.skipad.util.NotificationHelper

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsRepository.init(this)
        StatsRepository.init(this)
        LogStore.init(this)
        NotificationHelper.ensureChannels(this)
    }
}
