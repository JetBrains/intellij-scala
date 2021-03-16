package org.jetbrains.plugins.scala.tasty

import java.io.File
import scala.quoted.{Quotes, quotes}
import scala.tasty.inspector.TastyInspector

private class TastyImpl extends TastyApi {
  def read(classpath: String, className: String, rightHandSide: Boolean): Option[TastyFile] = {
    // TODO An ability to detect errors, https://github.com/lampepfl/dotty-feature-requests/issues/101
    var result = Option.empty[TastyFile]

    // TODO TASTy inspect: provide an option to reuse the compiler instance, https://github.com/lampepfl/dotty-feature-requests/issues/97
    val inspector = new TastyInspector {
      override def processCompilationUnit(using Quotes)(tree: quotes.reflect.Tree): Unit = {
        val printer = new SourceCode.SourceCodePrinter[quotes.type](SyntaxHighlight.plain, rightHandSide)
        val text = printer.printTree(tree).result()
        def file(path: String) = {
          val i = path.replace('\\', '/').lastIndexOf("/")
          if (i > 0) path.substring(i + 1) else path
        }
        val source = printer.sources.headOption.map(file).getOrElse("unknown.scala")
        result = Some(TastyFile(source, text, printer.references, printer.types))
      }
    }

    // See the comments in scala.tasty.inspector.TastyInspector
    // We use the private method rather than the public API because we want to pass FQN rather than .tasty file path,
    val method = classOf[TastyInspector].getDeclaredMethod("inspectFiles", classOf[List[String]], classOf[List[String]])
    method.setAccessible(true)
    method.invoke(inspector, classpath.split(File.pathSeparator).toList, List(className))

    result
  }
}