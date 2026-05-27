package app.phonetube.core.media.pending

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingActionsRepositoryTest {
    @Test
    fun `enqueue increases pending count`() = runTest {
        val dao = FakePendingActionDao()
        val repository = PendingActionsRepository.forTest(dao)
        repository.enqueuePayload(
            PendingActionType.LIKE,
            """{"videoId":"abc123"}"""
        )
        assertEquals(1, repository.pendingCount.value)
    }
}

private class FakePendingActionDao : PendingActionDao {
    private val items = mutableListOf<PendingActionEntity>()
    private var nextId = 1L

    override suspend fun all(): List<PendingActionEntity> = items.sortedBy { it.createdAtMs }

    override suspend fun insert(entity: PendingActionEntity): Long {
        val id = nextId++
        items += entity.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun count(): Int = items.size

    override suspend fun clearAll() {
        items.clear()
    }
}
