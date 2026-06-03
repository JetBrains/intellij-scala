package org.jetbrains.plugins.scala.codeInsight.delegate
import org.jetbrains.plugins.scala.ScalaVersion

class ScalaDelegateMethodTest_Scala3 extends ScalaDelegateMethodTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testCaretInTheEndOfIndentationBasedSyntax(): Unit = {
    doTest(
      s"""class D:
        |  def foo: String = null
        |
        |class A:
        |  private val d: D = ???
        |  $CARET""".stripMargin,
      """class D:
        |  def foo: String = null
        |
        |class A:
        |  private val d: D = ???
        |
        |  def foo: String = d.foo
        |  """.stripMargin
    )
  }
}
