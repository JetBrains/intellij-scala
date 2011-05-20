package org.jetbrains.plugins.scala.extensions.implementation

import collection.generic.CanBuildFrom

/**
 * Pavel Fatin
 */

class IterableExt[CC[X] <: Iterable[X], A](value: CC[A]) {
  private type CanBuildTo[Elem, CC[X]] = CanBuildFrom[Nothing, Elem, CC[Elem]]

  def zipMapped[B](f: A => B)(implicit cbf: CanBuildTo[(A, B), CC]): CC[(A, B)] = {
    val b = cbf()
    val it = value.iterator
    while (it.hasNext) {
      val v = it.next()
      b += ((v, f(v)))
    }
    b.result()
  }
}
