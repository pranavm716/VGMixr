package io.github.pranavm716.vgmixr.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlin.coroutines.CoroutineContext

/** An in-memory database whose suspend queries run on [queryContext] (the test dispatcher). */
fun inMemoryDatabase(queryContext: CoroutineContext): VGMixrDatabase = Room
    .inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        VGMixrDatabase::class.java
    )
    .setQueryCoroutineContext(queryContext)
    .build()
