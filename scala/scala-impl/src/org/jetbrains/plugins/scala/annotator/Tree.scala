package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.extensions._

private sealed trait Tree[+T] {
  def flattenTo(lengthOf: Tree[T] => Int, maxLength: Int): Seq[Tree[T]] = flattenTo0(lengthOf, maxLength)._1

  protected def flattenTo0(lengthOf: Tree[T] => Int, maxLength: Int): (Seq[Tree[T]], Int)
}

private object Tree {
  final case class Node[+T](children: Seq[Tree[T]]) extends Tree[T] {
    override def flattenTo0(lenghtOf: Tree[T] => Int, maxLength: Int): (Seq[Tree[T]], Int) = {
      val (xs, length) = children.reverse.foldlr(0, (Vector.empty[Tree[T]], 0))((l, x) => l + lenghtOf(x)) { case (l, x, (acc, r)) =>
        val (xs, length) = x.flattenTo0(lenghtOf, maxLength - l - r)
        (acc ++ xs, length + r)
      }
      val nodeLength = lenghtOf(this)
      if (length <= maxLength.max(nodeLength)) (xs, length) else (Seq(Node[T](xs)), nodeLength)
    }
  }

  final case class Leaf[+T](element: T) extends Tree[T] {
    override protected def flattenTo0(lenghtOf: Tree[T] => Int, maxLength: Int): (Seq[Tree[T]], Int) = (Seq(this), lenghtOf(this))
  }
}