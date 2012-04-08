package org.jetbrains.plugins.scala.extensions.implementation

import collection.generic.CanBuildFrom

/**
 * Pavel Fatin
 */

class SeqExt[CC[X] <: Seq[X], A](value: CC[A]) {
  private type CanBuildTo[Elem, CC[X]] = CanBuildFrom[Nothing, Elem, CC[Elem]]

  def distinctBy[K](f: A => K)(implicit cbf: CanBuildTo[A, CC]): CC[A] = {
    val b = cbf()
    var seen = Set[K]()
    for (x <- value) {
      val v = f(x)
      if (!(seen contains v)) {
        b += x
        seen = (seen + v)
      }
    }
    b.result()
  }

  def mapWithIndex[B](f: (A, Int) => B)(implicit cbf: CanBuildTo[B, CC]): CC[B] = {
    val b = cbf()
    var i = 0
    for (x <- value) {
      b += f(x, i)
      i += 1
    }
    b.result()
  }

  def foreachWithIndex[B](f: (A, Int) => B) {
    var i = 0
    for (x <- value) {
      f(x, i)
      i += 1
    }
  }
}
