package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.Assert
import scala.collection.JavaConverters._


/**
  * Created by Svyatoslav Ilinskiy on 13.07.16.
  */
class ScalaHighlightUsagesHandlerTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testReturn(): Unit = {
    val code =
      """
        |object AAA {
        |  def foo(): Int = {
        |    if (true) {
        |      retu<caret>rn 1
        |    }
        |    2
        |  }
        |}
      """.stripMargin
    doTest(code, Seq("return 1", "2", "return"))
  }

  def testDef(): Unit = {
    val code =
      """
        |object AAA {
        |  de<caret>f foo(): Int = {
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
      """
        |object AAA {
        |  va<caret>l foo: Int = {
        |    if (true) 1
        |    else 2
        |  }
        |}
      """.stripMargin
    doTest(code, Seq("2", "1", "val"))
  }

  def testMutiValDeclaration(): Unit = {
    val code =
      """
        |object AAA {
        |  va<caret>l (i, j) = (1, 2)
        |}
      """.stripMargin
    assertHandlerIsNull(code)
  }


  def testVar(): Unit = {
    val code =
      """
        |object Zoo {
        |  va<caret>r x = 10
        |}
      """.stripMargin
    doTest(code, Seq("10", "var"))
  }

  def testVarMultiDeclaration(): Unit = {
    val code =
      """
        |object Zoo {
        |  v<caret>ar (x, y) = (1, 2)
        |}
      """.stripMargin
    assertHandlerIsNull(code)
  }

  def testCase(): Unit = {
    val code =
      """
        |object Koo {
        |  def foo(s: Any): Unit = s match {
        |    ca<caret>se _ =>
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
      """
        |object Koo {
        |  def foo(s: Any): Unit = s m<caret>atch {
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
      """
        |object Zoo {
        |  def foo(r: () => Unit): Unit = {
        |    t<caret>ry {
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
      """
        |object Zoo {
        |  def foo(s: Seq[String]): Unit = {
        |    fo<caret>r (a <- s) yield a
        |  }
        |}
      """.stripMargin
    doTest(code, Seq("a", "for"))
  }

  def testIf(): Unit = {
    val code =
      """
        |object Zoo {
        |  def foo(s: Seq[String]): Unit = {
        |    i<caret>f (s.isEmpty) println("empty")
        |    else println(s)
        |  }
        |}
      """.stripMargin
    doTest(code, Seq("println(s)", "println(\"empty\")", "if"))
  }

  def testAnonymousFunction(): Unit = {
    val code =
      """
        |object Zoo {
        |  () =<caret>> {
        |    println("A")
        |    1
        |  }
        |}
      """.stripMargin
    doTest(code, Seq("1", "=>"))
  }

  def testObject(): Unit = {
    val code =
      """
        |o<caret>bject Zoo {
        |  val x = 10
        |  var z = -1
        |}
      """.stripMargin
    doTest(code, Seq("10", "-1", "object"))
  }

  def testClass(): Unit = {
    val code =
      """
        |c<caret>lass Zoo(val b: Int) {
        |  10
        |  val x = 10
        |  var z = -1
        |}
      """.stripMargin
    doTest(code, Seq("10", "-1", "10", "class"))
  }

  def testTrait(): Unit = {
    val code =
      """
        |tra<caret>it Zoo {
        |  10
        |  val x = 10
        |  var z = -1
        |}
      """.stripMargin
    doTest(code, Seq("10", "-1", "10", "trait"))
  }

  def assertHandlerIsNull(fileText: String): Unit = {
    myFixture.configureByText("dummy.scala", fileText)
    assert(createHandler == null)
  }

  def doTest(fileText: String, expected: Seq[String]): Unit = {
    myFixture.configureByText("dummy.scala", fileText)
    val handler = createHandler
    val targets = handler.getTargets
    Assert.assertEquals(1, targets.size())
    handler.computeUsages(targets)
    val actualUsages: Seq[String] = handler.getReadUsages.asScala.map(_.substring(getFile.getText))
    Assert.assertEquals(s"actual: $actualUsages, expected: $expected", expected, actualUsages)
  }

  def createHandler: HighlightUsagesHandlerBase[PsiElement] = {
    HighlightUsagesHandler.createCustomHandler(getEditor, getFile).asInstanceOf[HighlightUsagesHandlerBase[PsiElement]]
  }

}
