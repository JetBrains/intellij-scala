package org.jetbrains.plugins.scala.packagesearch.api

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class AsyncExpirableCache<K, V>(
    cs: CoroutineScope,
    maxSize: Long = 10_000,
    expirationTimeout: Duration = 10.minutes,
    computeValue: suspend (K) -> V,
) {
    private val cache = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expirationTimeout.toJavaDuration())
        .buildAsync<K, V> { key, _ -> cs.future { computeValue(key) } }

    fun get(key: K): CompletableFuture<V> = cache.get(key)

    @ApiStatus.Internal
    @VisibleForTesting
    internal fun updateCache(key: K, value: CompletableFuture<V>) = cache.put(key, value)
}
