package org.jetbrains.plugins.scala
package util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import org.jetbrains.plugins.scala.util.UnloadableThreadLocal.startCleanupLoad

final class UnloadableThreadLocal[T >: Null](init: => T) {
  private val untilNextCleanup = new AtomicInteger(startCleanupLoad)
  private val references = new ConcurrentHashMap[Thread, T]

  def value: T = {
    references.computeIfAbsent(Thread.currentThread(), _ => {
      if (untilNextCleanup.decrementAndGet() == 0) {
        removeDeadThreadData()
        untilNextCleanup.set(references.size())
      }
      init
    })
  }

  def value_=(newValue: T): Unit = {
    references.put(Thread.currentThread(), newValue)
  }

  def withValue[R](newValue: T)(body: => R): R = {
    val save = value
    value = newValue
    val result = body
    value = save
    result
  }

  private def removeDeadThreadData(): Unit = {
    val keys = references.keys()

    while (keys.hasMoreElements) {
      val thread = keys.nextElement()
      if (!thread.isAlive) {
        references.remove(thread)
      }
    }
  }
}

object UnloadableThreadLocal {
  private val startCleanupLoad = 20

  def apply[T >: Null](init: => T): UnloadableThreadLocal[T] = new UnloadableThreadLocal(init)
}