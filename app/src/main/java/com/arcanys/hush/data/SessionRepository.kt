package com.arcanys.hush.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val sessionDao: SessionDao) {
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    suspend fun insert(session: SessionEntity) {
        sessionDao.insertSession(session)
    }
}
