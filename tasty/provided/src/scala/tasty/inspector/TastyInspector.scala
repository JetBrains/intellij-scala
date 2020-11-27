package scala.tasty.inspector

import scala.quoted.Quotes

// Reproduces the API & ABI of https://github.com/lampepfl/dotty/blob/M2/tasty-inspector/src/scala/tasty/inspector/TastyInspector.scala
trait TastyInspector {
  protected def processCompilationUnit(context: Quotes)(root: context.reflect.Tree): Unit

  // Scala 2 expects "init" method in a trait with a concrete method.
  // https://github.com/scala/scala-dev/issues/642
  // https://www.scala-lang.org/2019/12/18/road-to-scala-3.html
  // https://gitter.im/lampepfl/dotty?at=5e457f6c63c15921f4787d4d
//  def inspect(classpath: String, classes: List[String]): Unit = ???
}
