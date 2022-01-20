package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.UnpickleException
import org.jetbrains.plugins.scala.tasty.Node.{Node2, Node3}

class TastyImpl extends TastyApi {
  private val treePrinter = new TreePrinter()

  override def read(bytes: Array[Byte]): Option[(String, String)] = {
    try {
      val tree = TreeReader.treeFrom(bytes)
      // TODO Integrate source file parsing into TreePrinter
      import dotty.tools.tasty.TastyFormat._
      val sourceName = tree.nodes.collectFirst {
        case Node3(ANNOTATION, _, Seq(Node2(TYPEREF, Seq("SourceFile")), Node3(APPLY, _, Seq(_, Node2(STRINGconst, Seq(path)))))) =>
          val i = path.replace('\\', '/').lastIndexOf("/")
          if (i > 0) path.substring(i + 1) else path
      }
      Some((sourceName.getOrElse("Unknown.scala"), treePrinter.textOf(tree)))
    } catch {
      // In practice, this is needed in order to skip Dotty 0.27
      case _: UnpickleException => None
    }
  }
}