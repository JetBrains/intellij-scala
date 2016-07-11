package org.jetbrains.plugins.scala.codeInspection.unused

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.{VarCouldBeValInspection, VarToValFix}

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class VarCouldBeValInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[VarCouldBeValInspection]

  override protected def annotation: String = VarCouldBeValInspection.Annotation


  def testPrivateField(): Unit = {
    val code =
      s"""
         |class Foo {
         |  private ${START}var s = 0$END
         |  println(s)
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Foo {
        |  private var s = 0
        |  println(s)
        |}
      """.stripMargin
    val after =
      """
        |class Foo {
        |  private val s = 0
        |  println(s)
        |}
      """.stripMargin
    testFix(before, after, VarToValFix.Hint)
  }

  def testLocalVar(): Unit = {
    val code =
      s"""
         |object Foo {
         |  def foo(): Unit = {
         |    ${START}var s = 0$END
         |    val z = s
         |  }
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |object Foo {
        |  def foo(): Unit = {
        |    var s = 0
        |    val z = s
        |  }
        |}
      """.stripMargin
    val after =
      """
        |object Foo {
        |  def foo(): Unit = {
        |    val s = 0
        |    val z = s
        |  }
        |}
      """.stripMargin
    testFix(before, after, VarToValFix.Hint)
  }

  def testNonPrivateField(): Unit = {
    val code =
      """
        |class Foo {
        |  var s: String = ""
        |  protected var z: Int = 2
        |  println(s)
        |  println(z)
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testMultiDeclaration(): Unit = {
    val code =
      s"""
         |class Foo {
         |  private ${START}var (a, b): String = ???$END
         |  println(b)
         |  println(a)
         |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |class Foo {
        |  private var (a, b): String = ???
        |  println(b)
        |  println(a)
        |}
      """.stripMargin
    val after =
      """
        |class Foo {
        |  private val (a, b): String = ???
        |  println(b)
        |  println(a)
        |}
      """.stripMargin
    testFix(before, after, VarToValFix.Hint)
  }

  def testSupressed(): Unit = {
    val code =
      """
        |class Bar {
        |  //noinspection VarCouldBeVal
        |  private var f = 2
        |  println(f)
        |
        |  def aa(): Unit = {
        |    //noinspection VarCouldBeVal
        |    var d = 2
        |    val s = d
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testAssignmentDetectedNoError(): Unit = {
    val code =
      """
        |object Moo {
        | def method(): Unit = {
        |   var b = 1
        |   b.+=(2)
        |
        |   var c = 1
        |   c += 2
        | }
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testAdd(): Unit = {
    val code =
      s"""
        |object Koo {
        |  def foo(): Unit = {
        |    ${START}var d = 1$END
        |    d + 1
        |  }
        |}
      """.stripMargin
    checkTextHasError(code)
    val before =
      """
        |object Koo {
        |  def foo(): Unit = {
        |    var d = 1
        |    d + 1
        |  }
        |}
      """.stripMargin
    val after =
      """
        |object Koo {
        |  def foo(): Unit = {
        |    val d = 1
        |    d + 1
        |  }
        |}
      """.stripMargin
    testFix(before, after, VarToValFix.Hint)
  }
}
