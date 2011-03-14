package org.jetbrains.plugins.scala.extensions.implementation

import collection.generic.CanBuildFrom

/**
 * Pavel Fatin
 */

class TraversableExt[CC[X] <: Traversable[X], A](value: CC[A]) {
  private type CanBuildTo[Elem, CC[X]] = CanBuildFrom[Nothing, Elem, CC[Elem]]

  def filterBy[T](aClass: Class[T])(implicit cbf: CanBuildTo[T, CC]): CC[T] =
    value.filter(aClass.isInstance(_)).map[T, CC[T]](_.asInstanceOf[T])(collection.breakOut)

  def findBy[T](aClass: Class[T]): Option[T] =
    value.find(aClass.isInstance(_)).map(_.asInstanceOf[T])
}
