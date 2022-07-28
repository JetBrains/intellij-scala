package org.jetbrains.jps.incremental.scala
package local

import java.lang.ref.SoftReference
import java.util
import java.util.Map.Entry

class Cache[K, V](capacity: Int) {
  private val lock = new Object()

  private val map = new util.LinkedHashMap[K, SoftReference[V]](capacity, 0.75F, true) {
    override def removeEldestEntry(eldest: Entry[K, SoftReference[V]]): Boolean = size > capacity
  }

  def getOrUpdate(key: K)(value: => V): V = lock.synchronized {
    Option(map.get(key)).flatMap(reference => Option(reference.get())).getOrElse {
      val v = value
      map.put(key, new SoftReference(v))
      v
    }
  }
}
