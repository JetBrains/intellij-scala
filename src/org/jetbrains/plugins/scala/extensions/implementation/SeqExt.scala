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
}
