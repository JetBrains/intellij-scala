package org.jetbrains.plugins.scala.util

import com.intellij.util.containers.ContainerUtil

import java.util.concurrent.ConcurrentMap

/**
 * An alternative implementation of [[java.lang.ThreadLocal]] that lives completely in our plugin's class loader.
 * For use anywhere you would use a platform `ThreadLocal`.
 *
 * The idea is, when the Scala plugin is unloaded from IntelliJ IDEA, all references to thread local values
 * that hold instances of classes originating from the Scala plugin can be garbage collected.
 *
 * After closely examining and discussing the implementation of [[java.lang.ThreadLocal]], we have determined
 * that there is a risk of our classes still being reachable after unloading, if instances of classes of the
 * Scala plugin are kept in platform thread local values. This is because the platform thread local hash tables
 * that hold references to values use strong references to the values. Additionally, threads in IntelliJ IDEA
 * are pooled. This means that the threads will outlive our unloaded plugin and still keep strong references to
 * instances of classes from the Scala plugin.
 */
private[scala] final class UnloadableThreadLocal[T](init: => T) {
  // Every native thread has a single JVM Thread instance associated with it, so we can safely use the instances
  // as keys in a map (and don't have to resort to their id, for example).
  private val references: ConcurrentMap[Thread, T] = ContainerUtil.createConcurrentWeakMap[Thread, T]()

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

  def update(f: T => T): Unit = {
    val oldValue = value
    val newValue = f(oldValue)
    value = newValue
  }
}
