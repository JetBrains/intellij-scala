package org.jetbrains.plugins.scala
package util

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import org.jetbrains.plugins.scala.extensions._

import scala.annotation.tailrec

final class UnloadableThreadLocal[T >: Null](init: => T) {
  val references = new ConcurrentLinkedQueue[AtomicReference[T]]
  private val inner = new ThreadLocal[AtomicReference[T]] {
    override def initialValue(): AtomicReference[T] = {
      val ref = new AtomicReference(init)
      references.add(ref)
      ref
    }
  }

  @tailrec
  def value: T = {
    val ref = inner.get()
    ref.get match {
      case null =>
        val value = init
        if (ref.compareAndSet(null, value)) {
          // we set the new value so return it
          value
        } else {
          // someone else got in between getting null
          // and trying to set the value created by us, so fetch the other value
          this.value
        }
      case value => value
    }
  }

  def value_=(newValue: T): Unit = {
    assert(newValue != null)
    inner.get().set(newValue)
  }

  def withValue[R](newValue: T)(body: => R): R = {
    val save = value
    value = newValue
    val result = body
    value = save
    result
  }

  def clearAll(): Unit = {
    Iterator.continually(references.poll())
      .takeWhile(_ != null)
      .foreach(_.set(null))
  }

  invokeOnAnyPluginUnload {
    clearAll()
  }
}
object UnloadableThreadLocal {
  def apply[T >: Null](init: => T): UnloadableThreadLocal[T] = new UnloadableThreadLocal(init)
}