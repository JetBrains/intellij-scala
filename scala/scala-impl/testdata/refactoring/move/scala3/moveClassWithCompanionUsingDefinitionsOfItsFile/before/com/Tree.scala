package com

case class Leaf[T](var data: T)

class Tree

object Tree {
  def size(node: Any): Int = node match {
    case Leaf(_) => 1
    case _ => 0
  }
}
