package org.jetbrains.plugins.scala.caches.stats
import java.util

object ScalaCacheTracerDataSource extends DataSource[TracerData] {

  override def isActive: Boolean = Tracer.isEnabled

  override def stop(): Unit = Tracer.setEnabled(false)

  override def resume(): Unit = Tracer.setEnabled(true)

  override def clear(): Unit = Tracer.clearAll()

  override def getCurrentData: util.List[TracerData] = Tracer.getCurrentData
}
