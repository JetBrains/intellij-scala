package org.jetbrains.plugins.scala.packagesearch.util

import com.github.benmanes.caffeine.cache.{AsyncLoadingCache, Caffeine}
import org.jetbrains.plugins.scala.packagesearch.util.AsyncExpirableCache.{ExpirationTimeout, MaxSize}

import java.time.Duration
import java.util.concurrent.{CompletableFuture, ExecutorService}

final class AsyncExpirableCache[K, V](maxSize: Long,
                                      expirationTimeout: Duration,
                                      executor: ExecutorService,
                                      computeValue: K => V) {
  def this(executor: ExecutorService, computeValue: K => V) =
    this(MaxSize, ExpirationTimeout, executor, computeValue)

  private[this] val cache: AsyncLoadingCache[K, V] = Caffeine.newBuilder()
    .maximumSize(maxSize)
    .expireAfterWrite(expirationTimeout)
    .executor(executor)
    .buildAsync(computeValue(_))

  def get(key: K): CompletableFuture[V] = cache.get(key)
}

object AsyncExpirableCache {
  private val MaxSize = 10_000L
  private val ExpirationTimeout = Duration.ofMinutes(10)
}
