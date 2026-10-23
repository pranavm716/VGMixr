package io.github.pranavm716.vgmixr.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlin.coroutines.CoroutineContext

/**
 * An in-memory database, with the built-in Favorites playlist as a new app database has it, whose suspend queries run on [queryContext] (the test dispatcher). That
 * dispatcher runs on Robolectric's main thread, hence allowMainThreadQueries.
 */
fun inMemoryDatabase(queryContext: CoroutineContext): VGMixrDatabase = Room
    .inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        VGMixrDatabase::class.java
    )
    .setQueryCoroutineContext(queryContext)
    .allowMainThreadQueries()
    .addCallback(SeedFavorites)
    .build()
