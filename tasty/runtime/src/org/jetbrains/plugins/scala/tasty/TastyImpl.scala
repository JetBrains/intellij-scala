package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.UnpickleException

class TastyImpl extends TastyApi {
  private val treePrinter = new TreePrinter()

  override def read(bytes: Array[Byte]): Option[(String, String)] = {
    try {
      val tree = TreeReader.treeFrom(bytes)
      Some(treePrinter.textOf(tree))
    } catch {
      // In practice, this is needed in order to skip Dotty 0.27
      case _: UnpickleException => None
    }
  }
}