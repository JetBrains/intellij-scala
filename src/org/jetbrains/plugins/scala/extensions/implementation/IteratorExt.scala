package org.jetbrains.plugins.scala.extensions.implementation

/**
 * Pavel Fatin
 */

class IteratorExt[A](delegate: Iterator[A]) {
  def findByType[T](aClass: Class[T]): Option[T] =
    delegate.find(aClass.isInstance(_)).map(_.asInstanceOf[T])

  def filterByType[T](aClass: Class[T]): Iterator[T] =
    delegate.filter(aClass.isInstance(_)).map(_.asInstanceOf[T])
}
