package org.jetbrains.plugins.scala.caches.stats
import org.jetbrains.plugins.scala.extensions._

import java.util

object ScalaCacheMemoryDataSource extends DataSource[MemoryData] {
  override def isActive: Boolean = Tracer.isEnabled

  override def stop(): Unit = Tracer.setEnabled(false)

  override def resume(): Unit = Tracer.setEnabled(true)

  override def clear(): Unit =  CacheTracker.clearTracking()

  override def getCurrentData: util.List[MemoryData] = {
    val arrayBuffer = new util.ArrayList[MemoryData]
    CacheTracker.tracked.foreach {
      case (_, tracker) =>
        val tracked = tracker.tracked
        val capabilities = tracker.capabilities
        val data = MemoryData(
          tracker.id,
          tracker.name + (if (tracker.alwaysTrack) " (always tracked)" else ""),
          tracked.length,
          tracked.foldLeft(0) { _ + capabilities.cachedEntitiesCount(_)}
        )
        arrayBuffer.add(data)
    }
    arrayBuffer
  }
}
