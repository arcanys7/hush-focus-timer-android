package com.arcanys.hush

import android.app.Application
import com.arcanys.hush.data.AppDatabase
import com.arcanys.hush.data.SessionRepository

class HushApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { SessionRepository(database.sessionDao()) }
}
