package com.hex

class Tree private[com](val size: Int)

object Tree {
  private[com] def empty: Tree = new Tree(0)
}
