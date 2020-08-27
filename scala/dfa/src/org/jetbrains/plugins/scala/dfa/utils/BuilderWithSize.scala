package org.jetbrains.plugins.scala.dfa.utils

import scala.collection.{Factory, mutable}

class BuilderWithSize[-A, +To](private val inner: mutable.Builder[A, To]) extends mutable.Builder[A, To] {
  private var _addedElements = 0

  def elementsAdded: Int = _addedElements

  override def addOne(elem: A): this.type = {
    _addedElements += 1
    inner.addOne(elem)
    this
  }

  override def clear(): Unit = inner.clear()
  override def result(): To = inner.result()
  override def sizeHint(size: Int): Unit = inner.sizeHint(size)
  override def knownSize: Int = inner.knownSize
}

object BuilderWithSize {
  class PreFactory[T] {
    def apply[C](factory: Factory[T, C]): BuilderWithSize[T, C] =
      new BuilderWithSize(factory.newBuilder)
  }

  def newBuilder[T]: PreFactory[T] = new PreFactory
}