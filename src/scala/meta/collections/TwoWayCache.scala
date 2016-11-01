package scala.meta.collections

import scala.collection.mutable

/**
  * @author mucianm 
  * @since 03.06.16.
  */

class TwoWayCache[T, U] {
  private lazy val t2u = mutable.WeakHashMap[T, U]()
  private lazy val u2t = mutable.WeakHashMap[U, T]()

  def apply(t: T)(implicit ev: OverloadHack1) = t2u(t)
  def get(t: T)(implicit ev: OverloadHack1) = t2u.get(t)
  def contains(t: T)(implicit ev: OverloadHack1) = t2u.contains(t)
  def update(t: T, u: U)(implicit ev: OverloadHack1) = { t2u(t) = u; u2t(u) = t }
  def getOrElseUpdate(t: T, op: => U)(implicit ev: OverloadHack1) = {
    val u = t2u.getOrElseUpdate(t, op)
    u2t(u) = t
    u
  }

  def apply(u: U)(implicit ev: OverloadHack2) = u2t(u)
  def get(u: U)(implicit ev: OverloadHack2) = u2t.get(u)
  def contains(u: U)(implicit ev: OverloadHack2) = u2t.contains(u)
  def update(u: U, t: T)(implicit ev: OverloadHack2) = { u2t(u) = t; t2u(t) = u; u2t(u) = t }
  def getOrElseUpdate(u: U, op: => T)(implicit ev: OverloadHack2) = {
    val t = u2t.getOrElseUpdate(u, op)
    t2u(t) = u
    t
  }
}

object TwoWayCache {
  def apply[T, U]() = new TwoWayCache[T, U]()
}
