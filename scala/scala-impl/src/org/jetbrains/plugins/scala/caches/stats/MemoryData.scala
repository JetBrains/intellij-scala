package org.jetbrains.plugins.scala.caches.stats

case class MemoryData(id: String,
                      name: String,
                      trackedCaches: Int,
                      trackedCacheEntries: Int)
