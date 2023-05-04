package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.UnpickleException

class TastyImpl {
  def read(bytes: Array[Byte]): Option[(String, String)] = {
    try {
      val treePrinter = new TreePrinter()
      val tree = TreeReader.treeFrom(bytes)
      Some(treePrinter.fileAndTextOf(tree))
    } catch {
      // In practice, this is needed in order to skip Dotty 0.27
      case _: UnpickleException => None
      case _: StackOverflowError => None
    }
  }
}