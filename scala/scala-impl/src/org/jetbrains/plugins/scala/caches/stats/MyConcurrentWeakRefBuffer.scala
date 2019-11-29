package org.jetbrains.plugins.scala
package caches
package stats

import java.util

import scala.collection.JavaConverters._

class MyConcurrentWeakRefBuffer[T] {
  // make this data structure sync better!
  private[this] val monitor = new Object

  // this should be an immutable singly linked list
  private[this] val valueMap = new util.WeakHashMap[T, Null]

  def add(value: T): Unit = monitor.synchronized {
    valueMap.put(value, null)
  }

  def values: List[T] = monitor.synchronized {
    valueMap.keySet().asScala.toList
  }

  def clear(): List[T] = monitor.synchronized {
    val oldValues = values
    valueMap.clear()
    oldValues
  }
}
