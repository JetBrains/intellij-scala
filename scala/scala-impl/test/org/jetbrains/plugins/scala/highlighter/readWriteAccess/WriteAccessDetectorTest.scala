package org.jetbrains.plugins.scala.highlighter.readWriteAccess

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

class WriteAccessDetectorTest extends ScalaLightCodeInsightFixtureTestCase {
  private trait Access
  private object Write extends Access
  private object Read extends Access

  private def doTest(code: String, accesses: Seq[(String, Access)]): Unit = {
    val file = myFixture.configureByText("Foo.scala", code)
    val actual = file.depthFirst()
      .filterByType[ScReferenceExpression]
      .map(ref => ref.getText -> (if (ScalaReadWriteAccessDetector.isAccessedForWriting(ref)) Write else Read))
      .toSeq

    def makeText(accesses: Seq[(String, Access)]): String =
      accesses
        .map {
          case (text, access) => s"$text: $access"
        }
        .mkString("\n")

    makeText(actual) shouldBe makeText(accesses)
  }

  def test_simple_read(): Unit = doTest(
    """
      |object Foo {
      |  val x = 1
      |  x
      |  println(x)
      |}
      |""".stripMargin,
    Seq(
      "x" -> Read,
      "println" -> Read,
      "x" -> Read,
    )
  )

  def test_simple_write(): Unit = doTest(
    """
      |object Foo {
      |  var x = 1
      |  var y = 1
      |  x = y
      |  y = x
      |}
      |""".stripMargin,
    Seq(
      "x" -> Write,
      "y" -> Read,
      "y" -> Write,
      "x" -> Read,
    )
  )

  def test_update_binary_expr(): Unit = doTest(
    """
      |object Foo {
      |  var x = 1
      |  x += 1
      |  x *= 1
      |  x >>= 1
      |  x <<<<<<<<<<<<<= 1 // not a valid operator, but should still be detected
      |}
      |""".stripMargin,
    Seq(
      "x" -> Write,
      "+=" -> Read,
      "x" -> Write,
      "*=" -> Read,
      "x" -> Write,
      ">>=" -> Read,
      "x" -> Read,
      "<<<<<<<<<<<<<=" -> Read,
    )
  )

  def test_non_update_binary_expr(): Unit = doTest(
    """object Bar {
      |  def += (x: Int): Unit = ()
      |}
      |
      |object Foo {
      |  Bar += 1
      |}
      |""".stripMargin,
    Seq(
      "Bar" -> Read,
      "+=" -> Read,
    )
  )
}
