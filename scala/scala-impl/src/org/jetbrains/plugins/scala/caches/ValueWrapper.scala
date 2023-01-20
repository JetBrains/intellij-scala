package org.jetbrains.plugins.scala.caches

import com.intellij.util.SofterReference

trait ValueWrapper[A] {
  type Reference

  def wrap(v: A): Reference

  def unwrap(v: Reference): A
}

object ValueWrapper {
  def None[A]: ValueWrapper[A] = new ValueWrapper[A] {
    override type Reference = A

    override def wrap(v: A): Reference = v

    override def unwrap(v: Reference): A = v
  }

  def SofterReference[A]: ValueWrapper[A] = new ValueWrapper[A] {
    override type Reference = SofterReference[A]

    override def wrap(v: A): Reference = new SofterReference[A](v)

    override def unwrap(v: Reference): A = v.get()
  }
}
