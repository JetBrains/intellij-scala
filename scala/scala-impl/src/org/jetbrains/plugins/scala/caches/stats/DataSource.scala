package org.jetbrains.plugins.scala.caches.stats

trait DataSource[Data] {
  def isActive: Boolean

  def stop(): Unit

  def resume(): Unit

  def clear(): Unit

  def getCurrentData: java.util.List[Data]
}