package org.jetbrains.plugins.scala
package lang
package imports
package unused

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaLightCodeInsightFixtureTestAdapter}

/**
 * Created by Svyatoslav Ilinskiy on 24.07.16.
 */
class UnusedImportTest extends ScalaLightCodeInsightFixtureTestAdapter with AssertMatches {

  def testTwoUnusedSelectorsOnSameLine(): Unit = {
    val text =
      """
        |import java.util.{Set, ArrayList}
        |
        |object Doo
      """.stripMargin

    doTest(text) {
      case "import java.util.{Set, ArrayList}" :: Nil =>
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

    doTest(text) {
      case Nil =>
    }
  }

  def testMethodCallImplicitParameter(): Unit = {
    val text =
      """import scala.concurrent.ExecutionContext
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |object Test {
        |  def foo(implicit ec: ExecutionContext): Unit = {}
        |
        |  foo
        |}""".stripMargin


    doTest(text) {
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

    doTest(text) {
      case Nil =>
    }
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

    doTest(text) {
      case Nil =>
    }
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

    doTest(text) {
      case "X => Z" :: Nil =>
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

    doTest(text) {
      case "s => implicitString" :: Nil =>
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

    doTest(text) {
      case "X => Z" :: Nil =>
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

    doTest(text) {
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

    doTest(text) {
      case "_" :: Nil =>
    }
  }

  def testWorkSheet(): Unit = {
    val text = "import scala.Seq"
    doTest(text, worksheet.WorksheetFileType) {
      case `text` :: Nil =>
    }
  }

  private def doTest(text: String,
                     fileType: LanguageFileType = ScalaFileType.INSTANCE)
                    (pattern: PartialFunction[List[String], Unit]): Unit = {
    myFixture.configureByText(fileType, text)

    import collection.JavaConverters._
    val messages = myFixture.doHighlighting()
      .asScala
      .toList
      .filterNot(_.getDescription == null)
      .map(_.getText)

    assertMatches(messages)(pattern)
  }
}
