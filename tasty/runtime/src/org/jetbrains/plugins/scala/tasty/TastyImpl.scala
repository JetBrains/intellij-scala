package org.jetbrains.plugins.scala.tasty

import java.io.File
import scala.quoted.{Quotes, quotes}
import scala.tasty.inspector.Inspector
import scala.tasty.inspector.Tasty

private class TastyImpl extends TastyApi {
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

  def read(classpath: String, tastyFile: String, rightHandSide: Boolean): Option[TastyFile] = {
    // TODO An ability to detect errors, https://github.com/lampepfl/dotty-feature-requests/issues/101
    var result = Option.empty[TastyFile]

    // TODO TASTy inspect: provide an option to reuse the compiler instance, https://github.com/lampepfl/dotty-feature-requests/issues/97
    val inspector = new Inspector {
      override def inspect(using Quotes)(tastys: List[Tasty[quotes.type]]): Unit = tastys.foreach { tasty =>
        val printer = new SourceCode.SourceCodePrinter[quotes.type](SyntaxHighlight.plain, fullNames = true, rightHandSide)
        val text = printer.printTree(tasty.ast).result()
        def file(path: String) = {
          val i = path.replace('\\', '/').lastIndexOf("/")
          if (i > 0) path.substring(i + 1) else path
        }
        val source = printer.sources.headOption.map(file).getOrElse("unknown.scala")
        result = Some(TastyFile(source, text, printer.references, printer.types))
      }
    }

    // See the comments in scala.tasty.inspector.TastyInspector
    val aClass = Class.forName("scala.tasty.inspector.TastyInspector$")
    val method = aClass.getDeclaredMethod("inspectFiles", classOf[List[String]], classOf[List[String]], classOf[Inspector])
    method.setAccessible(true)
    val module = aClass.getField("MODULE$").get(null)
    method.invoke(module, classpath.split(File.pathSeparator).toList, List(tastyFile), inspector)

    result
  }
}