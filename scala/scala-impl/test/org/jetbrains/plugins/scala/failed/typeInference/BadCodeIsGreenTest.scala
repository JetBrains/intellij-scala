package org.jetbrains.plugins.scala.failed.typeInference

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection

/**
  * User: Dmitry.Naydanov
  * Date: 22.03.16.
  */
abstract class BadCodeIsGreenTest extends ScalaInspectionTestBase {

  override protected def shouldPass: Boolean = false

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AnnotatorBasedErrorInspection]
}

class Test1 extends BadCodeIsGreenTest {

  override protected val description: String =
    "‘A’ has itself as bound"

  def testScl7139_1(): Unit = {
    checkTextHasError(
      s"""
         |class X1[${START}A >: A$END]
      """.stripMargin)
  }
  
  def testScl7139_2(): Unit = {
    checkTextHasError(
      s"""
         |class X2[A <: B, B <: C, ${START}C <: A$END]
      """.stripMargin)
  }
}

class Test2 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch: expected (=> TicketTester.A) => TicketTester.B, found: TicketTester.A => TicketTester.B"

  def testScl1731(): Unit = {
    checkTextHasError(
      s"""
         |object Test {
         |  class A
         |  class B
         |  val a: (=> A) => B = $START(x: A) => new B$END
         |}
       """.stripMargin)
  }
}

class Test5 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: M[T], actual: Main.B4"

  def testSCL7618(): Unit = {
    checkTextHasError(
      s"""
        |object Main extends App {
        |  trait A[T]
        |  trait C[T]
        |  trait E extends A[Int]
        |  trait F extends C[Int]
        |  class B1 extends E with F
        |  class B2 extends F with E
        |  class B3 extends A[Int] with C[Int]
        |  class B4 extends C[Int] with A[Int]
        |  def foo[T, M[_] <: A[_]](x: M[T]): M[T] = x
        |
        |  foo(new B1)
        |  foo(new B2)
        |  foo(new B3)
        |  foo(${START}new B4$END)
        |}
      """.stripMargin)
  }
}

class Test7 extends BadCodeIsGreenTest {

  override protected val description: String =
    "class type required but Hello.type found"

  def testSCL10320(): Unit = {
    checkTextHasError(
      s"""
        |object Hello {
        |  val foobar = new Foobar()
        |  foobar.notRed(Array(classOf[${START}Hello.type$END], classOf[Object]))
        |}
        |
        |class Foobar {
        |  def notRed(someArray: Array[Class[_]]): Foobar = {
        |    someArray foreach (c ⇒ println(c.getCanonicalName))
        |    this
        |  }
        |}
      """.stripMargin)
  }
}

class Test8 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: (String, Any), actual: String(\"b\")"

  def testSCL10438(): Unit = {
    checkTextHasError(
      s"""
        |object Stuff {
        |  val parameters = Map[String, Any]("a", $START"b"$END)
        |}
      """.stripMargin)
  }
}

class Test9 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: Option[?], actual: Seq[Int]"

  def testSCL10583(): Unit = {
    checkTextHasError(
      s"""
        |Some(1) flatMap (${START}Seq(_)$END)
      """.stripMargin)
  }
}
