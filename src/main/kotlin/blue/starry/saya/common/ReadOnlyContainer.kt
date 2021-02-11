package blue.starry.saya.common

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import kotlin.time.minutes

class ReadOnlyContainer<T: Any>(private val block: suspend () -> List<T>?) {
    private val mutex = Mutex()
    private val collection = mutableListOf<T>()
    private val logger = KotlinLogging.createSayaLogger("saya.ReadOnlyContainer")

    private val initialJob = GlobalScope.launch {
        collection.addAll(block() ?: return@launch)
        logger.debug { "Initial update job for ${this@ReadOnlyContainer::class.simpleName} was finished." }
    }

    init {
        GlobalScope.launch {
            while (isActive) {
                delay(Env.SAYA_UPDATE_INTERVAL_MINS.minutes)
                update()
                logger.debug { "Regular update job for ${this@ReadOnlyContainer::class.simpleName} was finished." }
            }
        }
    }

    suspend fun update() {
        initialJob.join()

        mutex.withLock {
            val new = block() ?: return

            collection.clear()
            collection.addAll(new)
        }
    }

    suspend fun add(new: T) {
        initialJob.join()

        mutex.withLock {
            collection.add(new)
        }
    }

    suspend fun replace(new: T, predicate: (T) -> Boolean) {
        initialJob.join()

        mutex.withLock {
            val index = collection.indexOfFirst(predicate)

            if (index < 0) {
                add(new)
            } else {
                collection[index] = new
            }
        }
    }

    suspend fun find(predicate: (T) -> Boolean): T? {
        initialJob.join()

        return mutex.withLock {
            collection.find(predicate)
        }
    }

    suspend fun filter(predicate: (T) -> Boolean): List<T> {
        initialJob.join()

        return mutex.withLock {
            collection.filter(predicate)
        }
    }

    suspend fun toList(): List<T> {
        initialJob.join()

        return mutex.withLock {
            collection.toList()
        }
    }
}
