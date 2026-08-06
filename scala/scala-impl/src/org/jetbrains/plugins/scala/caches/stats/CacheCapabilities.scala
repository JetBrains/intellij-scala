package org.jetbrains.plugins.scala.caches.stats

trait CacheCapabilities[T] {
  type CacheType = T

  def cachedEntitiesCount(cache: CacheType): Int
  def clear(cache: CacheType): Unit
}
