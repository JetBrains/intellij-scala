package org.jetbrains.plugins.scala.lang.imports.unused

import org.jetbrains.plugins.scala.base.AssertMatches

/**
  * Created by Svyatoslav Ilinskiy on 24.07.16.
  */
class UnusedImportTest extends UnusedImportTestBase with AssertMatches {
  def testTwoUnusedSelectorsOnSameLine(): Unit = {
    val text =
      """
        |import java.util.{Set, ArrayList}
        |
        |object Doo
      """.stripMargin
    assertMatches(messages(text)) {
      case HighlightMessage("import java.util.{Set, ArrayList}", _) :: Nil =>
    }
  }

  def testUsedImportFromInterpolatedString(): Unit = {
    val text =
      """
        |object theObj {
        |  implicit class StringConversion(val sc: StringContext) {
        |    def zzz(args: Any*): String = {
        |      "blabla"
        |    }
        |  }
        |}
        |
        |class MainTest {
        |  import theObj._
        |  def main(args: Array[String]): Unit = {
        |    val s: String = zzz"blblablas"
        |  }
        |}
      """.stripMargin
    assertMatches(messages(text)) {
      case Nil =>
    }
  }

  def testMethodCallImplicitParameter(): Unit = {
    val text = """import scala.concurrent.ExecutionContext
      |import scala.concurrent.ExecutionContext.Implicits.global
      |
      |object Test {
      |  def foo(implicit ec: ExecutionContext): Unit = {}
      |
      |  foo
      |}""".stripMargin
    assertMatches(messages(text)) {
      case Nil =>
    }
  }

  def testSCL9538(): Unit = {
    val text =
      """
        |import scala.concurrent.ExecutionContext
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |class AppModel(implicit ec: ExecutionContext) {
        |
        |}
        |
        |val x = new AppModel
      """.stripMargin
    assert(messages(text).isEmpty)
  }
}
