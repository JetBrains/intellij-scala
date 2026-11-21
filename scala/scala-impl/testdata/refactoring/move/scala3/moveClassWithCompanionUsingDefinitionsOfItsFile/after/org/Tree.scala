package org

import com.Leaf

class Tree

object Tree {
  def size(node: Any): Int = node match {
    case Leaf(_) => 1
    case _ => 0
  }
}