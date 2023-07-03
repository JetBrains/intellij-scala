package org.jetbrains.plugins.scala
package highlighter
package usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert

import scala.jdk.CollectionConverters._

class ScalaHighlightUsagesHandlerTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_3_0

  val | = CARET

  def testReturn(): Unit = {
    val code =
      s"""
        |object AAA {
        |  def foo(): Int = {
        |    if (true) {
        |      retu${|}rn 1
        |    }
        |    2
        |  }
        |}
      """.stripMargin
    doTest(code, Seq("return 1", "2", "return"))
  }

  def testDef(): Unit = {
    val code =
      s"""
         |object AAA {
         |  de${|}f foo(): Int = {
         |    if (true) {
         |      return 1
         |    }
         |    2
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("return 1", "2", "def"))
  }

  def testVal(): Unit = {
    val code =
      s"""
         |object AAA {
         |  va${|}l foo: Int = {
         |    if (true) 1
         |    else 2
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("2", "1", "val"))
  }

  def testMutiValDeclaration(): Unit = {
    val code =
      s"""
         |object AAA {
         |  va${|}l (i, j) = (1, 2)
         |}
       """.stripMargin
    assertHandlerIsNull(code)
  }


  def testVar(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  va${|}r x = 10
         |}
       """.stripMargin
    doTest(code, Seq("10", "var"))
  }

  def testVarMultiDeclaration(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  v${|}ar (x, y) = (1, 2)
         |}
       """.stripMargin
    assertHandlerIsNull(code)
  }

  def testCase(): Unit = {
    val code =
      s"""
         |object Koo {
         |  def foo(s: Any): Unit = s match {
         |    ca${|}se _ =>
         |      println(1)
         |      println(s)
         |    case _ => ()
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("println(s)", "case"))
  }

  def testMatch(): Unit = {
    val code =
      s"""
         |object Koo {
         |  def foo(s: Any): Unit = s m${|}atch {
         |    case _ =>
         |      println(1)
         |      println(s)
         |    case _ => ()
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("println(s)", "()", "match"))
  }

  def testTry(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  def foo(r: () => Unit): Unit = {
         |    t${|}ry {
         |      r()
         |    } catch {
         |      case _: IndexOutOfBoundsException => println(":(")
         |    }
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("r()", "println(\":(\")", "try"))
  }

  def testFor(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  def foo(s: Seq[String]): Unit = {
         |    fo${|}r (a <- s) yield a
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("a", "for"))
  }

  def testIfWithElse(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  def foo(s: Seq[String]): Unit = {
         |    i${|}f (s.isEmpty) println("empty")
         |    else println(s)
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("println(s)", "println(\"empty\")", "if"))
  }

  def testIfWithoutElse(): Unit =
    assertHandlerIsNull(
      s"""
         |object Zoo {
         |  def foo(s: Seq[String]): Unit = {
         |    i${|}f (s.isEmpty)
         |      println("empty")
         |  }
         |}
       """.stripMargin
    )

  def testAnonymousFunction(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  () =${|}> {
         |    println("A")
         |    1
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("1", "=>"))
  }

  def testCaseClauseInAnonymousFunction(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  () => {
         |    test {
         |      case 3 =${|}>
         |    }
         |    1
         |  }
         |}
       """.stripMargin
    assertHandlerIsNull(code)
  }


  def testCaseClause(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  () => {
         |    test {
         |      case 3 =${|}>
         |        val x = 3
         |        x
         |    }
         |    1
         |  }
         |}
       """.stripMargin
    doTest(code, Seq("x", "=>"))
  }

  def testCompanionObject(): Unit = {
    val code =
      s"""
         |o${|}bject Zoo
         |class Zoo
       """.stripMargin
    doTest(code, Seq("object", "class"))
  }

  def testCompanionClass(): Unit = {
    val code =
      s"""
         |c${|}lass Zoo
         |object Zoo
       """.stripMargin
    doTest(code, Seq("class", "object"))
  }

  def testCompanionTrait(): Unit = {
    val code =
      s"""
         |tra${|}it Zoo
         |object Zoo
         |}
       """.stripMargin
    doTest(code, Seq("trait", "object"))
  }

  def testSCL20883(): Unit = {
    val code =
      s"""
         |enum Color {
         |  case Red
         |}
         |object A {
         |  Color.Re${|}d
         |}
         |""".stripMargin

    doTest(code, Seq("Red", "Red"))
  }

  def assertHandlerIsNull(fileText: String): Unit = {
    myFixture.configureByText("dummy.scala", fileText)
    assert(createHandler() == null)
  }

  def doTest(fileText: String, expected: Seq[String]): Unit = {
    myFixture.configureByText("dummy.scala", fileText)
    val handler = createHandler()
    val targets = handler.getTargets
//    Assert.assertEquals(1, targets.size())
    handler.computeUsages(targets)
    val actualUsages: Seq[String] = handler.getReadUsages.asScala.map(_.substring(getFile.getText)).toSeq
    Assert.assertEquals(s"actual: $actualUsages, expected: $expected", expected, actualUsages)
  }

  def createHandler(): HighlightUsagesHandlerBase[PsiElement] = {
    HighlightUsagesHandler.createCustomHandler(getEditor, getFile).asInstanceOf[HighlightUsagesHandlerBase[PsiElement]]
  }

}
