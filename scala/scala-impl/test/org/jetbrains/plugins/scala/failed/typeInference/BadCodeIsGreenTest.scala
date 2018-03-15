package org.jetbrains.plugins.scala.failed.typeInference

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection
import org.junit.experimental.categories.Category

/**
  * User: Dmitry.Naydanov
  * Date: 22.03.16.
  */
abstract class BadCodeIsGreenTest extends ScalaInspectionTestBase {

  override protected def shouldPass: Boolean = false

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AnnotatorBasedErrorInspection]
}

@Category(Array(classOf[PerfCycleTests]))
class Test1 extends BadCodeIsGreenTest {

  override protected val description: String =
    "‘A’ has itself as bound"

  def testScl7139_1() {
    checkTextHasError(
      s"""
         |class X1[${START}A >: A$END]
      """.stripMargin)
  }
  
  def testScl7139_2() {
    checkTextHasError(
      s"""
         |class X2[A <: B, B <: C, ${START}C <: A$END]
      """.stripMargin)
  }
}

@Category(Array(classOf[PerfCycleTests]))
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

@Category(Array(classOf[PerfCycleTests]))
class Test3 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch: expected Future[?], found Option[Int]"

  def testScl8684(): Unit = {
    checkTextHasError(
      """object Test {
        |  def foo() = {
        |    for {
        |      x <- Future(1)
        |      y <- Option(1)
        |    } yield x + y
        |  }
        |}
      """.stripMargin)
  }
}

@Category(Array(classOf[PerfCycleTests]))
class Test4 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Reference to a is ambiguous; it is both a method parameter and a variable in scope."

  def testSCL4434(): Unit = {
    checkTextHasError(
      """
        |object Foo {
        |  def foo(a: Any) = a;
        |  var a = 1;
        |  foo(a = 2)
        |}
      """.stripMargin)
  }
}

@Category(Array(classOf[PerfCycleTests]))
class Test5 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: M[T], actual: Main.B4"

  def testSCL7618(): Unit = {
    checkTextHasError(
      """
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
        |  foo(new B4)
        |}
      """.stripMargin)
  }
}

@Category(Array(classOf[PerfCycleTests]))
class Test6 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: Foo, actual: String"

  def testSCL8983(): Unit = {
    checkTextHasError(
      """
        |class Foo extends ((String,String) => String) with Serializable{
        |  override def apply(v1: String, v2: String): String = {
        |    v1+v2
        |  }
        |}
        |
        |object main extends App {
        |  val x = "x"
        |  val y = "y"
        |  val string: Foo = new Foo()(x,y)
        |}
      """.stripMargin)
  }
}

@Category(Array(classOf[PerfCycleTests]))
class Test7 extends BadCodeIsGreenTest {

  override protected val description: String =
    "class type required but Hello.type found"

  def testSCL10320(): Unit = {
    checkTextHasError(
      """
        |object Hello {
        |  val foobar = new Foobar()
        |  foobar.notRed(Array(classOf[Hello.type], classOf[Object]))
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

@Category(Array(classOf[PerfCycleTests]))
class Test8 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: (String, Any), actual: String(\"b\")"

  def testSCL10438(): Unit = {
    checkTextHasError(
      """
        |object Stuff {
        |  val parameters = Map[String, Any]("a", "b")
        |}
      """.stripMargin)
  }
}

@Category(Array(classOf[PerfCycleTests]))
class Test9 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: Option[?], actual: Seq[Int]"

  def testSCL10583(): Unit = {
    checkTextHasError(
      """
        |Some(1) flatMap (Seq(_))
      """.stripMargin)
  }
}

@Category(Array(classOf[PerfCycleTests]))
class Test10 extends BadCodeIsGreenTest {

  override protected val description: String =
    "Type mismatch, expected: Int, actual: Unit"

  def testSCL10608(): Unit = {
    checkTextHasError(
      """
        |def abc = {
        |    23
        |    val b = "nope"
        |  }
        |def foo(par: Int) = ???
        |foo(abc)
      """.stripMargin)
  }
}
