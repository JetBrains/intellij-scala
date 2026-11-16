package com.hex

class Tree private[hex](val size: Int)

object Tree {
  private[hex] def empty: Tree = new Tree(0)
}
