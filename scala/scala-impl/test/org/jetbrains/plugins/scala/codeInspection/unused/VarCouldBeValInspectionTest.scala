package org.jetbrains.plugins.scala.codeInspection
package unused

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.VarCouldBeValInspection

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class VarCouldBeValInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[VarCouldBeValInspection]

  import VarCouldBeValInspection._

  override protected val description: String = DESCRIPTION

  def testPrivateField(): Unit = testQuickFix(
    text =
      s"""
         |class Foo {
         |  private ${START}var$END s = 0
         |  println(s)
         |}
        """.stripMargin,
    expected =
      """
        |class Foo {
        |  private val s = 0
        |  println(s)
        |}
      """.stripMargin
  )

  def testLocalVar(): Unit = testQuickFix(
    text =
      s"""
         |object Foo {
         |  def foo(): Unit = {
         |    ${START}var$END s = 0
         |    val z = s
         |  }
         |}
      """.stripMargin,
    expected =
      """
        |object Foo {
        |  def foo(): Unit = {
        |    val s = 0
        |    val z = s
        |  }
        |}
      """.stripMargin
  )

  def testNonPrivateField(): Unit = checkTextHasNoErrors(
    text =
      """
        |class Foo {
        |  var s: String = ""
        |  protected var z: Int = 2
        |  println(s)
        |  println(z)
        |}
      """.stripMargin
  )

  def testMultiDeclaration(): Unit = testQuickFix(
    text =
      s"""
         |class Foo {
         |  private ${START}var$END (a, b): String = ???
         |  println(b)
         |  println(a)
         |}
      """.stripMargin,
    expected =
      """
        |class Foo {
        |  private val (a, b): String = ???
        |  println(b)
        |  println(a)
        |}
      """.stripMargin
  )

  def testSuppressed(): Unit = checkTextHasNoErrors(
    text =
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
  )

  def testAssignmentDetectedNoError(): Unit = checkTextHasNoErrors(
    text =
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
  )

  def testAdd(): Unit = testQuickFix(
    text =
      s"""
         |object Koo {
         |  def foo(): Unit = {
         |    ${START}var$END d = 1
         |    d + 1
         |  }
         |}
      """.stripMargin,
    expected =
      """
        |object Koo {
        |  def foo(): Unit = {
        |    val d = 1
        |    d + 1
        |  }
        |}
      """.stripMargin
  )

  private def testQuickFix(text: String, expected: String): Unit =
    testQuickFix(text, expected, VarToValFix.HINT)
}
