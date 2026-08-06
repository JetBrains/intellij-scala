package org.jetbrains.plugins.scala.caches.stats

import java.util.concurrent.atomic.AtomicReference

private class MyConcurrentMap[K, V >: Null] {
  private def emptyMap = java.util.Collections.emptyMap[K, V]()

  private val ref: AtomicReference[java.util.Map[K, V]] = new AtomicReference(emptyMap)

  def computeIfAbsent(k: K, v: K => V): V = {
    do {
      val prev = ref.get()
      prev.get(k) match {
        case null =>
          val newValue = v(k)
          val newMap = add(prev, k, newValue)
          if (ref.compareAndSet(prev, newMap))
            return newValue
        case v =>
          return v
      }
    } while (true)
    //will be never reached
    null
  }

  def clear(): Unit = ref.set(emptyMap)

  def map[T](f: (K, V) => T): java.util.List[T] = {
    val map = ref.get()
    val result = new java.util.ArrayList[T]
    map.forEach((k, v) => result.add(f(k, v)))
    result
  }

  private def add(oldMap: java.util.Map[K, V], key: K, value: V): java.util.Map[K, V] = {
    val newMap = new java.util.HashMap[K, V](oldMap)
    newMap.put(key, value)
    java.util.Collections.unmodifiableMap(newMap)
  }
}