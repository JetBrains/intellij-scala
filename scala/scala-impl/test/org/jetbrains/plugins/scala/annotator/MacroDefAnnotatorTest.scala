package org.jetbrains.plugins.scala
package annotator

class MacroDefAnnotatorTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_13

  override protected val includeReflectLibrary: Boolean = true

  private def doTest(text: String)(expectedErrors: Message*): Unit = {
    val errors = errorsFromScalaCode(text)
    assertMessages(errors)(expectedErrors: _*)
  }

  def testMacroDef(): Unit = doTest(
    """
      |object Test {
      |  import scala.reflect.macros._
      |  def helloWorld = macro helloImpl
      |
      |  def helloImpl(c: blackbox.Context): c.Expr[Unit] = { ??? }
      |}
      |""".stripMargin)(Error("helloWorld", "Macro defs must have explicitly specified return types"))


  def testHasExplicitType(): Unit = doTest(
    """
      |object Test {
      |  import scala.reflect.macros._
      |  def helloWorld: Unit = macro helloImpl
      |
      |  def helloImpl(c: blackbox.Context): c.Expr[Unit] = { ??? }
      |}
      |""".stripMargin)()
}
