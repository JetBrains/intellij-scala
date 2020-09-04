package scala.tasty.compat

import scala.language.reflectiveCalls

// Adds "compat" extensions to https://github.com/lampepfl/dotty/blob/master/tasty-inspector/src/scala/tasty/inspector/TastyInspector.scala
private trait TastyInspector extends scala.tasty.inspector.TastyInspector {
  override protected final def processCompilationUnit(reflect: scala.tasty.Reflection)(tree: reflect.Tree): Unit = {
    // There are no extension methods in Scala 2.x, so we have to add them separately on top of the ABI.
    val reflectWrapper = new Reflection(reflect)
    processCompilationUnit0(reflectWrapper)(tree.asInstanceOf[reflectWrapper.delegate.Tree])
  }

  protected def processCompilationUnit0(reflect: Reflection)(tree: reflect.delegate.Tree): Unit

  def inspect0(classpath: String, classes: List[String]): Unit = {
    // See the comments in scala.tasty.inspector.TastyInspector
    val thisInstance = this.asInstanceOf[ {def inspect(classpath: String, classes: List[String]): Unit}]
    thisInstance.inspect(classpath, classes)
  }
}
