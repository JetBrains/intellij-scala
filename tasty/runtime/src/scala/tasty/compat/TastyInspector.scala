package scala.tasty.compat

import java.io.File
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

  def inspect0(classpath: String, classes: List[String]): Unit = {
    // See the comments in scala.tasty.inspector.TastyInspector
    // We use the private method rather than the public API because we want to pass FQN rather than .tasty file path,
    val method = classOf[scala.tasty.inspector.TastyInspector].getDeclaredMethod("inspectFiles", classOf[List[String]], classOf[List[String]])
    method.setAccessible(true)
    method.invoke(this, classpath.split(File.pathSeparator).toList, classes)
  }
}
