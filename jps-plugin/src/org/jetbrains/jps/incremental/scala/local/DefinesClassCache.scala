package org.jetbrains.jps.incremental.scala.local

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import sbt.internal.inc.Locate
import xsbti.compile.DefinesClass

object DefinesClassCache {
  private var cacheStamp: Long = -1L
  private val cache = new ConcurrentHashMap[File, DefinesClass]()

  def definesClassFor(file: File): DefinesClass = {
    Option(cache.get(file)).getOrElse {
      val newDefines = Locate.definesClass(file)
      cache.put(file, newDefines)
      newDefines
    }
  }

  def invalidateCacheIfRequired(stamp: Long) = synchronized {
    if (stamp != cacheStamp)
      cache.clear()

    cacheStamp = stamp
  }
}
