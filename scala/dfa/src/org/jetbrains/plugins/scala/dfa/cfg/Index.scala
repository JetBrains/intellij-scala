package org.jetbrains.plugins.scala.dfa
package cfg

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions


final class Index private(val indices: ArraySeq[Int]) extends AnyVal with Ordered[Index] {
  override def compare(that: Index): Int = {
    import Ordering.Implicits.seqOrdering
    implicitly[Ordering[ArraySeq[Int]]]
      .compare(indices, that.indices)
  }

  def withInner(inner: Int): Index = new Index(indices :+ inner)

  def apply(idx: Int): Int = indices(idx)
}

object Index {
  def apply(indices: Int*): Index = new Index(indices.to(ArraySeq))

  implicit def indexToIndexView(index: Index): IndexView = IndexView(index)
}

final case class IndexView(index: Index, start: Int = 0) {
  assert(start < index.indices.length)

  def top: Int = index(start)
  def inner: Option[IndexView] = {
    val innerStart = start + 1
    if (innerStart < index.indices.length) Some(copy(start = innerStart))
    else None
  }
}