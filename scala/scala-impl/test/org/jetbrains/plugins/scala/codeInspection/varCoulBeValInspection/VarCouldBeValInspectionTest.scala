package org.jetbrains.plugins.scala.codeInspection.varCoulBeValInspection

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.VarCouldBeValInspection
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class VarCouldBeValInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[VarCouldBeValInspection]

  override protected val description: String = ScalaInspectionBundle.message("var.could.be.a.val")

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

  def testTupled(): Unit = checkTextHasNoErrors(
    """object X {
      |  def foo(): Unit = {
      |    var (a, b) = (1, 2)
      |    b = 3
      |    assert(a == 3)
      |  }
      |}
    """.stripMargin
  )

  def testSeveralPatterns(): Unit = checkTextHasNoErrors(
    """object X {
      |  def foo(): Unit = {
      |    var a, b = 0
      |    b = 1
      |    assert(a == 0)
      |  }
      |}
    """.stripMargin
  )

  def testFor(): Unit = checkTextHasNoErrors(
    """object Foo {
      | def foo(): Int = {
      |   var result = 42
      |   for (_ <- 1 to 100500) {
      |     result = result + 1
      |   }
      |   result
      | }
      |}""".stripMargin
  )

  def testCustomPlus(): Unit = checkTextHasNoErrors(
    """class Y {
      |  def +(any: AnyRef): this.type = this
      |}
      |
      |{
      |  var y = new Y()
      |  y += 1
      |}
      |""".stripMargin
  )

  // SCL-17508
  def testCustomPlusEq(): Unit = testQuickFix(
    s"""class Y {
       |  def +=(any: AnyRef): Unit = ()
       |}
       |
       |{
       |  ${START}var$END y = new Y()
       |  y += 1
       |}
       |""".stripMargin,
    """class Y {
      |  def +=(any: AnyRef): Unit = ()
      |}
      |
      |{
      |  val y = new Y()
      |  y += 1
      |}
      |""".stripMargin,
  )

  private def testQuickFix(text: String, expected: String): Unit =
    testQuickFix(text, expected, ScalaInspectionBundle.message("convert.var.to.val"))
}
