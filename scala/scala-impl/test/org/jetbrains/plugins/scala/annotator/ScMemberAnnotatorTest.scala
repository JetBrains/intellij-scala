package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Message.Error

class ScMemberAnnotatorTest extends ScalaHighlightingTestBase {
  def test_ignored_wrong_toplevel_definition(): Unit =
    assertNoErrors(
      """
        |val test = 3
        |""".stripMargin
    )

  def test_wrong_toplevel_definition(): Unit =
    assertErrorsWithHints(
      """package test
        |
        |val x = 3
        |""".stripMargin,
      Error("val", "Cannot be a top-level definition in Scala 2")
    )

  def test_wrong_toplevel_definition_in_packaging(): Unit =
    assertErrorsWithHints(
      """package test {
        |  val x = 3
        |}
        |
        |val y = 6
        |""".stripMargin,
      Error("val", "Cannot be a top-level definition in Scala 2")
    )
}
