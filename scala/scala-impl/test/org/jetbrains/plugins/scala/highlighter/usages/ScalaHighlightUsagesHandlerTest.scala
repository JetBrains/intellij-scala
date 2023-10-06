package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ScalaHighlightUsagesHandlerTest extends ScalaHighlightUsagesHandlerTestBase {

  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_3_0

  override def createHandler: HighlightUsagesHandlerBase[PsiElement] =
    HighlightUsagesHandler.createCustomHandler(getEditor, getFile).asInstanceOf[HighlightUsagesHandlerBase[PsiElement]]

  def testReturn(): Unit = {
    val code =
      s"""
        |object AAA {
        |  def foo(): Int = {
        |    if (true) {
        |      retu${CARET}rn 1
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
         |  de${CARET}f foo(): Int = {
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
         |  va${CARET}l foo: Int = {
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
         |  va${CARET}l (i, j) = (1, 2)
         |}
       """.stripMargin
    assertHandlerIsNull(code)
  }


  def testVar(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  va${CARET}r x = 10
         |}
       """.stripMargin
    doTest(code, Seq("10", "var"))
  }

  def testVarMultiDeclaration(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  v${CARET}ar (x, y) = (1, 2)
         |}
       """.stripMargin
    assertHandlerIsNull(code)
  }

  def testCase(): Unit = {
    val code =
      s"""
         |object Koo {
         |  def foo(s: Any): Unit = s match {
         |    ca${CARET}se _ =>
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
         |  def foo(s: Any): Unit = s m${CARET}atch {
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
         |    t${CARET}ry {
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
         |    fo${CARET}r (a <- s) yield a
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
         |    i${CARET}f (s.isEmpty) println("empty")
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
         |    i${CARET}f (s.isEmpty)
         |      println("empty")
         |  }
         |}
       """.stripMargin
    )

  def testAnonymousFunction(): Unit = {
    val code =
      s"""
         |object Zoo {
         |  () =${CARET}> {
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
         |      case 3 =${CARET}>
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
         |      case 3 =${CARET}>
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
         |o${CARET}bject Zoo
         |class Zoo
       """.stripMargin
    doTest(code, Seq("object", "class"))
  }

  def testCompanionClass(): Unit = {
    val code =
      s"""
         |c${CARET}lass Zoo
         |object Zoo
       """.stripMargin
    doTest(code, Seq("class", "object"))
  }

  def testCompanionTrait(): Unit = {
    val code =
      s"""
         |tra${CARET}it Zoo
         |object Zoo
         |}
       """.stripMargin
    doTest(code, Seq("trait", "object"))
  }

// Handled by the default rather than a custom handler
//  def testSCL20883(): Unit = {
//    val code =
//      s"""
//         |enum Color {
//         |  case Red
//         |}
//         |object A {
//         |  Color.Re${CARET}d
//         |}
//         |""".stripMargin
//
//    doTest(code, Seq("Red", "Red"))
//  }
//
//  def testSCL20883CaseClassCase(): Unit = {
//    val code =
//      s"""
//         |enum Tree[+A] {
//         |  case Leaf
//         |  case Node(value: A, r: Tree[A], l: Tree[A])
//         |}
//         |
//         |object A {
//         |  val n = println(Tree.No${CARET}de(1, Tree.Leaf, Tree.Leaf))
//         |}
//         |""".stripMargin
//
//    doTest(code, Seq("Node", "Node"))
//  }
}
