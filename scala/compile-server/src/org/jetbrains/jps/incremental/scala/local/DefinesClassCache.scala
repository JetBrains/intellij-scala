package org.jetbrains.jps.incremental.scala.local

import java.util.concurrent.ConcurrentHashMap

import sbt.internal.inc.Locate
import xsbti.VirtualFile
import xsbti.compile.DefinesClass

object DefinesClassCache {
  private var cacheStamp: Long = -1L
  private val cache = new ConcurrentHashMap[VirtualFile, DefinesClass]()

  def definesClassFor(file: VirtualFile): DefinesClass = {
    Option(cache.get(file)).getOrElse {
      val newDefines = Locate.definesClass(file)
      cache.put(file, newDefines)
      newDefines
    }
  }

  def invalidateCacheIfRequired(stamp: Long): Unit = synchronized {
    if (stamp != cacheStamp)
      cache.clear()

    cacheStamp = stamp
  }
}
