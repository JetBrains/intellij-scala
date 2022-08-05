package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase

class ScFunctionAnnotatorTest extends ScalaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def test_transparent_method_without_inline_is_invalid():Unit = errorsFromScalaCode(
    s"""
       |class Aaa:
       |  transparent def foo(a: Int): Unit = {}
       |""".stripMargin).nonEmpty

  def test_transparent_method_with_inline_is_valid(): Unit = errorsFromScalaCode(
    s"""
       |class Aaa:
       |  transparent inline def foo(a: Int): Unit = {}
       |""".stripMargin).isEmpty

  def test_notinline_method_with_inline_argument_is_invalid(): Unit = errorsFromScalaCode(
    s"""
       |class Aaa:
       |  def foo(inline a: Int): Unit = {}
       |""".stripMargin).nonEmpty

  def test_inline_method_with_inline_argument_is_valid(): Unit = errorsFromScalaCode(
    s"""
       |class Aaa:
       |  inline def foo(inline a: Int): Unit = {}
       |""".stripMargin).isEmpty
}
