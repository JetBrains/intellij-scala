package org.jetbrains.plugins.scala
package caches
package stats

import java.util.concurrent.ConcurrentHashMap

import com.intellij.util.containers.WeakList
import org.jetbrains.plugins.scala.extensions._

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.collection


object CacheTracker {
  private class TrackedCacheTypeImpl[C](override val id: String,
                                        override val name: String,
                                        override val capabilities: CacheCapabilities[C],
                                        override val alwaysTrack: Boolean) extends TrackedCacheType {
    override type Cache = C

    private val trackedCaches: WeakList[Cache] = new WeakList[Cache]
    def add(cache: Cache): Unit = trackedCaches.add(cache)

    override def tracked: collection.Seq[Cache] = trackedCaches.toStrongList.asScala
    override def cachedEntityCount: Int = tracked.foldLeft(0)(_ + capabilities.cachedEntitiesCount(_))
    override def clear(): Unit = tracked.foreach(capabilities.clear)
  }

  private val trackedCacheTypes = new ConcurrentHashMap[String, TrackedCacheType]

  def isEnabled: Boolean = Tracer.isEnabled

  def alwaysTrack[Cache: ClassTag: CacheCapabilities](cacheTypeId: String, name: String)(cache: Cache): Cache = {
    track(cacheTypeId, name, cache, alwaysTrack = true)
    cache
  }

  def track[Cache: ClassTag: CacheCapabilities](cacheTypeId: => String, name: => String)(cache: Cache): Cache = {
    if (isEnabled) track(cacheTypeId, name, cache, alwaysTrack = false)
    cache
  }

  private def track[Cache: ClassTag: CacheCapabilities](cacheTypeId: String, name: String, cache: Cache, alwaysTrack: Boolean): Unit = {
    val cacheType =
      trackedCacheTypes
        .computeIfAbsent(cacheTypeId, new TrackedCacheTypeImpl[Cache](_, name, implicitly[CacheCapabilities[Cache]], alwaysTrack))
        .asInstanceOf[TrackedCacheTypeImpl[Cache]]
    cacheType.add(cache)
  }

  def isCacheTypeRegistered(cacheTypeId: String): Boolean = trackedCacheTypes.containsKey(cacheTypeId)

  def clearCacheOfType(cacheTypeId: String): Unit = {
    trackedCacheTypes.get(cacheTypeId).nullSafe.foreach(_.clear())
  }

  def clearAllCaches(): Unit = {
    trackedCacheTypes.forEach((_, trackedCacheType) => trackedCacheType.clear())
  }

  def clearTracking(): Unit = {
    val alwaysTracked = tracked.values.filter(_.alwaysTrack).toList
    trackedCacheTypes.clear()
    alwaysTracked.foreach(t => trackedCacheTypes.put(t.id, t))
  }

  def tracked: Map[String, TrackedCacheType] = {
    val mapBuilder = Map.newBuilder[String, TrackedCacheType]
    trackedCacheTypes.forEach(
      (cacheTypeId, trackedCacheType) => mapBuilder += cacheTypeId -> trackedCacheType
    )
    mapBuilder.result()
  }
}
