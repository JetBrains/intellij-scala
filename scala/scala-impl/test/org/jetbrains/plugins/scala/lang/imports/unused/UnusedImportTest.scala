package org.jetbrains.plugins.scala.lang.imports.unused

import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions

/**
  * Created by Svyatoslav Ilinskiy on 24.07.16.
  */
class UnusedImportTest extends UnusedImportTestBase with MatcherAssertions {
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

  def testTwoUnusedSelectorsInWorksheet(): Unit = {
    val text =
      """
        |import java.util.{Set, ArrayList}
        |
        |object Doo
      """.stripMargin
    assertMatches(messages(text, "dummy.sc")) {
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

  def testShadowAndWildcard(): Unit = {
    val text =
      """
        |object A {
        |  class X
        |  class Y
        |}
        |
        |import A.{X => _, _}
        |object B {
        |  new Y
        |}
      """.stripMargin
    assert(messages(text).isEmpty)
  }

  def testSelectorAndWildcard(): Unit = {
    val text =
      """
        |object A {
        |  class X
        |  class Y
        |}
        |
        |import A.{X => Z, _}
        |object B {
        |  new Y
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("X => Z", _) :: Nil =>
    }
  }

  def testUnusedImplicitSelectorAndWildcard(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |import A.{s => implicitString, X => Z, _}
        |object B {
        |  (new Y, new Z)
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("s => implicitString", _) :: Nil =>
    }
  }

  def testUnusedFoundImplicitSelectorAndWildcard(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |object B {
        |  import A.{s => implicitString, X => Z, _}
        |
        |  def foo(implicit s: String) = s
        |  foo
        |
        |  new Y
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("X => Z", _) :: Nil =>
    }
  }

  def testSelectorAndShadow(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |import A.{X => Z, s => _}
        |object B {
        |  new Z
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case Nil =>
    }
  }

  def testUnusedWildcard(): Unit = {
    val text =
      """
        |object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |import A.{Y, X => Z, s => _, _}
        |object B {
        |  (new Y, new Z)
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("_", _) :: Nil =>
    }
  }
}


class UnusedImportTest_InScala3 extends UnusedImportTestBase with MatcherAssertions {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  override protected def setUp(): Unit = {
    super.setUp()
    ScalaProjectSettings.getInstance(getProject).setCompilerHighlightingScala3(false)
  }

  def testUnusedWildcard(): Unit = {
    val text =
      """
        |object A {
        |  class X
        |  class Y
        |}
        |
        |import A.{Y, X as Z, *}
        |object B {
        |  (new Y, new Z)
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("*", _) :: Nil =>
    }
  }
}