package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase

class ScFunctionAnnotatorTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def test_transparent_method_without_inline_is_invalid(): Unit =
    assertErrorsText(
      s"""transparent def foo(a: Int): Unit = {}
         |""".stripMargin,
      """Error(transparent,The `transparent` keyword may only be used for inline methods)
        |""".stripMargin
    )

  def test_transparent_method_with_inline_is_valid(): Unit =
    assertNoErrors(
      s"""transparent inline def foo(a: Int): Unit = {}
         |""".stripMargin
    )

  def test_notinline_method_with_inline_argument_is_invalid(): Unit =
    assertErrorsText(
      s"""def foo(inline p1: String)
         |       (inline p2: String, p3: Int, inline p4: String)
         |       (p5: String)
         |       (p6: String, p7: Int): String = ???
         |""".stripMargin,
      """Error(inline,The `inline` modifier may only be used for arguments of inline methods)
        |Error(inline,The `inline` modifier may only be used for arguments of inline methods)
        |Error(inline,The `inline` modifier may only be used for arguments of inline methods)
        |""".stripMargin
    )

  def test_inline_method_with_inline_argument_is_valid(): Unit =
    assertNoErrors(
      s"""inline def foo(inline p1: String)
         |              (inline p2: String, p3: Int, inline p4: String)
         |              (p5: String)
         |              (p6: String, p7: Int): String = ???
         |""".stripMargin
    )
}
