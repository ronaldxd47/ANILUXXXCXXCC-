package com.example.data.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory TTL cache dengan in-flight request deduplication (coalescing).
 * Mencegah permintaan jaringan ganda ketika beberapa komponen meminta URL/key yang sama secara bersamaan.
 */
class TtlMemoryCache<K : Any, V : Any>(
    private val defaultTtlMs: Long,
    private val maxEntries: Int = 128
) {
    private data class Entry<V>(
        val value: V,
        val expiresAtMs: Long
    )

    private val entries = ConcurrentHashMap<K, Entry<V>>()
    private val inFlight = ConcurrentHashMap<K, Deferred<V?>>()
    private val mutex = Mutex()

    suspend fun getOrFetch(
        key: K,
        ttlMs: Long = defaultTtlMs,
        forceRefresh: Boolean = false,
        shouldCache: (V?) -> Boolean = { it != null },
        fetcher: suspend () -> V?
    ): V? {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = entries[key]
            if (cached != null && now < cached.expiresAtMs) {
                return cached.value
            }
        }

        val (deferred, isOwner) = mutex.withLock {
            if (!forceRefresh) {
                val cached = entries[key]
                if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
                    return cached.value
                }
            }
            val existing = inFlight[key]
            if (existing != null && existing.isActive) {
                existing to false
            } else {
                val created = CompletableDeferred<V?>()
                inFlight[key] = created
                created to true
            }
        }

        if (!isOwner) {
            return deferred.await()
        }

        return try {
            val result = fetcher()
            if (result != null && shouldCache(result)) {
                evictIfNeeded()
                entries[key] = Entry(result, System.currentTimeMillis() + ttlMs)
            }
            (deferred as CompletableDeferred<V?>).complete(result)
            result
        } catch (t: Throwable) {
            (deferred as CompletableDeferred<V?>).completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private fun evictIfNeeded() {
        val now = System.currentTimeMillis()
        entries.entries.removeIf { now >= it.value.expiresAtMs }
        while (entries.size >= maxEntries) {
            val oldestKey = entries.entries.minByOrNull { it.value.expiresAtMs }?.key ?: break
            entries.remove(oldestKey)
        }
    }

    fun clear() {
        entries.clear()
    }
}
