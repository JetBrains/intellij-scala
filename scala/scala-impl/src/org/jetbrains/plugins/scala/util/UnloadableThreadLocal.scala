package org.jetbrains.plugins.scala
package util

import com.intellij.util.containers.ContainerUtil

final class UnloadableThreadLocal[T >: Null](init: => T) {
  // Every thread has a single Thread-instance associated with them,
  // so we can safely use them directly as keys in a map
  // (and don't have to resort to their id, for example).
  private val references = ContainerUtil.createConcurrentWeakMap[Thread, T]

  def value: T = {
    references.computeIfAbsent(Thread.currentThread(), _ => init)
  }

  def value_=(newValue: T): Unit = {
    references.put(Thread.currentThread(), newValue)
  }

  def withValue[R](newValue: T)(body: => R): R = {
    val save = value
    value = newValue
    try body
    finally value = save
  }
}

object UnloadableThreadLocal {
  def apply[T >: Null](init: => T): UnloadableThreadLocal[T] = new UnloadableThreadLocal(init)
}