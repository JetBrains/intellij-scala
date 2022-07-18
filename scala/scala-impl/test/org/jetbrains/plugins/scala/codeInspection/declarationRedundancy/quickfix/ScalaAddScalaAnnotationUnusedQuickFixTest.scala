package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class ScalaAddScalaAnnotationUnusedQuickFixTest extends ScalaUnusedDeclarationInspectionTestBase {

  def test_field(): Unit = {
    val before =
      """
        |@scala.annotation.unused class Foo {
        |  val s = 0
        |}
      """.stripMargin
    val after =
      """
        |import scala.annotation.unused
        |
        |@scala.annotation.unused class Foo {
        |  @unused
        |  val s = 0
        |}
      """.stripMargin
    testQuickFix(before, after, addScalaAnnotationUnusedHint)
  }

  def test_class(): Unit = {
    val before =
      """
        |class Foo
      """.stripMargin
    val after =
      """
        |import scala.annotation.unused
        |
        |@unused
        |class Foo
      """.stripMargin
    testQuickFix(before, after, addScalaAnnotationUnusedHint)
  }

  def test_parameter(): Unit = {
    val before =
      """
        |import scala.annotation.unused
        |
        |@unused
        |object Foo {
        |  @unused
        |  def bar(i: Int) = println("")
        |}
        |""".stripMargin
    val after =
      """
        |import scala.annotation.unused
        |
        |@unused
        |object Foo {
        |  @unused
        |  def bar(@unused i: Int) = println("")
        |}
        |""".stripMargin
    testQuickFix(before, after, addScalaAnnotationUnusedHint)
  }
}
