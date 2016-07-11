package org.jetbrains.plugins.scala.codeInspection.unused

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.{DeleteUnusedElementFix, ScalaUnusedSymbolInspection}

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class ScalaUnusedSymbolInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ScalaUnusedSymbolInspection]

  override protected def annotation: String = ScalaUnusedSymbolInspection.Annotation

  def testPrivateField(): Unit = {
    val code =
      s"""
        |class Foo {
        |  private val ${START}s$END = 0
        |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Foo {
        |  private val s = 0
        |}
      """.stripMargin
    val after =
      """
        |class Foo {
        |}
      """.stripMargin
    testFix(before, after, DeleteUnusedElementFix.Hint)
  }

  def testLocalUnusedSymbol(): Unit = {
    val code =
      s"""
        |object Foo {
        |  def foo(): Unit = {
        |    val ${START}s$END = 0
        |  }
        |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |object Foo {
        |  def foo(): Unit = {
        |    val s = 0
        |  }
        |}
      """.stripMargin
    val after =
      """
        |object Foo {
        |  def foo(): Unit = {
        |  }
        |}
      """.stripMargin
    testFix(before, after, DeleteUnusedElementFix.Hint)
  }

  def testNonPrivateField(): Unit = {
    val code =
      """
         |class Foo {
         |  val s: String = ""
         |  protected val z: Int = 2
         |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testRemoveMultiDeclaration(): Unit = {
    val code =
      s"""
         |class Foo {
         |  private val (${START}a$END, b): String = ???
         |  println(b)
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
         |class Foo {
         |  private val (a, b): String = ???
         |  println(b)
         |}
      """.stripMargin
    val after =
      """
        |class Foo {
        |  private val (_, b): String = ???
        |  println(b)
        |}
      """.stripMargin
    testFix(before, after, DeleteUnusedElementFix.Hint)
  }

  def testSupressed(): Unit = {
    val code =
      """
        |class Bar {
        |  //noinspection ScalaUnusedSymbol
        |  private val f = 2
        |
        |  def aa(): Unit = {
        |    //noinspection ScalaUnusedSymbol
        |    val d = 2
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testLocalVar(): Unit = {
    val code =
      s"""
        |class Bar {
        |  def aa(): Unit = {
        |    var (${START}d$END, a) = 10
        |  }
        |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (d, a) = 10
         |    println(a)
         |  }
         |}
      """.stripMargin
    val after =
      s"""
         |class Bar {
         |  def aa(): Unit = {
         |    var (_, a) = 10
         |    println(a)
         |  }
         |}
      """.stripMargin
    testFix(before, after, DeleteUnusedElementFix.Hint)
  }
}
