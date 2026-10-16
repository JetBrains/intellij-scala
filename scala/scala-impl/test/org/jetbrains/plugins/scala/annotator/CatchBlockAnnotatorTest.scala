package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class CatchBlockAnnotatorTest_Scala3 extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3

  // `catch handler` stands for `catch { case e => handler(e) }`, so the handler needn't be a partial function
  def testFunctionAsHandler(): Unit = assertNoErrors(
    """object Test {
      |  def foo: Int = {
      |    val handler: Throwable => Int = _ => 2
      |    try ??? catch handler
      |  }
      |
      |  def bar: Int = {
      |    val handler: PartialFunction[Throwable, Int] = { case _ => 2 }
      |    try ??? catch handler
      |  }
      |}
      |""".stripMargin
  )

  def testHandlerOfWrongType(): Unit = assertErrorsText(
    """object Test {
      |  def foo: Int = {
      |    val handler: Int = 2
      |    try ??? catch handler
      |  }
      |}
      |""".stripMargin,
    "Error(handler,Method apply is not a member of type Int)"
  )
}
