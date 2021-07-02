package org.jetbrains.plugins.scala.tasty

class TastyImpl extends TastyApi {
  override def read(bytes: Array[Byte]): (String, String) = {
    // TODO Integrate source file parsing into TreePrinter
    val tree = TreeReader.treeFrom(bytes)
    import dotty.tools.tasty.TastyFormat._
    val sourceName = tree.nodes.collectFirst {
      case Node(ANNOTATION, _, Seq(Node(TYPEREF, Seq("SourceFile"), _: _*), Node(APPLY, _, Seq(_, Node(STRINGconst, Seq(path), _: _*))))) =>
        val i = path.replace('\\', '/').lastIndexOf("/")
        if (i > 0) path.substring(i + 1) else path
    }
    (sourceName.getOrElse("Unknown.scala"), TreePrinter.textOf(tree))
  }
}