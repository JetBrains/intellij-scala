package org.jetbrains.plugins.scala.caches

import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.caches.ValueWrapper.Reference

trait ValueWrapper {
  def wrap[A](v: A): Reference[A]
}

object ValueWrapper {
  trait Reference[+A] {
    def get(): A
  }

  val None: ValueWrapper = new ValueWrapper {
    override def wrap[A](v: A): Reference[A] = () => v
  }

  val SofterReference: ValueWrapper = new ValueWrapper {
    override def wrap[A](v: A): Reference[A] = {
      val reference = new SofterReference[A](v)
      () => reference.get()
    }
  }
}
