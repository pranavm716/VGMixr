package io.github.pranavm716.vgmixr.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val catalogDao = db.catalogDao()
    private val userDataDao = db.userDataDao()

    @After
    fun closeDb() = db.close()

    @Test
    fun `gamesInFamily orders by position and includes retired games`() = runTest(dispatcher) {
        catalogDao.upsertGames(
            listOf(
                game("emerald", position = 2),
                game("ruby", position = 0, retired = true),
                game("sapphire", position = 1),
                game("pla", position = 0, familyId = "pla")
            )
        )

        assertEquals(
            listOf("ruby", "sapphire", "emerald"),
            catalogDao.gamesInFamily("rse").map { it.id }
        )
    }

    @Test
    fun `gamesInFamily breaks position ties by id`() = runTest(dispatcher) {
        catalogDao.upsertGames(
            listOf(game("sapphire", position = 0), game("ruby", position = 0, retired = true))
        )

        assertEquals(listOf("ruby", "sapphire"), catalogDao.gamesInFamily("rse").map { it.id })
    }

    @Test
    fun `favorites and history round-trip`() = runTest(dispatcher) {
        val favorite = FavoriteEntity("rse-route-101", Instant.ofEpochMilli(1_000))
        userDataDao.insertFavorite(favorite)
        userDataDao.insertHistory(
            PlayHistoryEntity(trackId = "rse-route-101", playedAt = Instant.ofEpochMilli(2_000))
        )

        assertEquals(favorite, userDataDao.favorite("rse-route-101"))
        assertEquals(
            listOf(
                PlayHistoryEntity(
                    id = 1,
                    trackId = "rse-route-101",
                    playedAt = Instant.ofEpochMilli(2_000)
                )
            ),
            userDataDao.history()
        )
    }

    private fun game(
        id: String,
        position: Int,
        retired: Boolean = false,
        familyId: String = "rse"
    ) = GameEntity(
        id = id,
        name = id,
        shortName = id.take(1).uppercase(),
        color = "#000000",
        generation = 3,
        platform = "GBA",
        familyId = familyId,
        position = position,
        retired = retired
    )
}
