package org.jetbrains.plugins.scala
package util

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions._

final class UnloadableThreadLocal[T >: Null](init: => T) {
  private val unloaded = new AtomicBoolean(false)
  private val references = ContainerUtil.createConcurrentList[AtomicReference[T]]

  private val inner = new ThreadLocal[AtomicReference[T]] {
    override def initialValue(): AtomicReference[T] = {
      val initial = init
      assert(initial != null)
      val ref = new AtomicReference(initial)
      references.add(ref)
      if (unloaded.get()) {
        // plugin is in unloading process?
        ref.set(null)
        throw new AssertionError("Threadlocal should not be used when unloding is in progress")
      }
      ref
    }
  }

  invokeOnScalaPluginUnload {
    val alreadyUnloaded = unloaded.getAndSet(true)
    if (alreadyUnloaded) {
      throw new AssertionError("Thread local should not be cleared twice")
    }
    references.forEach(ref => {
      ref.set(null)
    })
  }

  def value: T = {
    val value = inner.get.get
    assert(value != null, "Thread local is already unloaded and cannot be used anymore!")
    value
  }

  def value_=(newValue: T): Unit = {
    require(newValue != null, "Cannot set null to thread local")
    val old = inner.get.getAndSet(newValue)
    assert(old != null, "Thread local is already unloaded and cannot be set anymore!")
  }

  def withValue[R](newValue: T)(body: => R): R = {
    val save = value
    value = newValue
    val result = body
    value = save
    result
  }
}

object UnloadableThreadLocal {
  def apply[T >: Null](init: => T): UnloadableThreadLocal[T] = new UnloadableThreadLocal(init)
}