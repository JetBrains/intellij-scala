package scala.tasty.compat

import scala.language.reflectiveCalls
import scala.quoted.QuoteContext

// Adds "compat" extensions to https://github.com/lampepfl/dotty/blob/master/tasty-inspector/src/scala/tasty/inspector/TastyInspector.scala
private trait TastyInspector extends scala.tasty.inspector.TastyInspector {
  override protected final def processCompilationUnit(context: QuoteContext)(root: context.reflect.Tree): Unit = {
    // There are no extension methods in Scala 2.x, so we have to add them separately on top of the ABI.
    val reflectWrapper = new Reflection(context.reflect)
    processCompilationUnit0(reflectWrapper)(root.asInstanceOf[reflectWrapper.delegate.Tree])
  }

  protected def processCompilationUnit0(reflect: Reflection)(tree: reflect.delegate.Tree): Unit

  def inspect0(jar: Option[String], filePaths: List[String]): Unit = {
    // See the comments in scala.tasty.inspector.TastyInspector
    val thisInstance = this.asInstanceOf[ {def inspectAllTastyFiles(tastyFiles: List[String], jars: List[String], dependenciesClasspath: List[String]): Boolean}]
    thisInstance.inspectAllTastyFiles(filePaths, jar.toList, List.empty)
  }
}
