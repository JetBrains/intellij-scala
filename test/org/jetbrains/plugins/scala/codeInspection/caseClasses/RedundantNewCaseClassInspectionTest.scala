package org.jetbrains.plugins.scala.codeInspection.caseClasses

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.syntacticSimplification.RedundantNewCaseClassInspection

/**
  * mattfowler
  * 5/7/2016
  */
class RedundantNewCaseClassInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  override protected def annotation: String = ScalaBundle.message("new.on.case.class.instantiation.redundant")

  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[RedundantNewCaseClassInspection]

  def testSimpleCaseClass(): Unit = {
    val program =
      s"""
         |case class A(x: Int)
         |val a = ${START}new$END A(5)
     """.stripMargin

    val expected =
      s"""
         |case class A(x: Int)
         |val a = A(5)
       """.stripMargin

    check(program)

    testFix(program, expected, annotation)
  }

  def testGenericCaseClass(): Unit = {
    val program =
      s"""
         |case class A[T](x: T)
         |val a = ${START}new$END A(5)
     """

    val expected =
      s"""
         |case class A[T](x: T)
         |val a = A(5)
       """.stripMargin

    check(program.stripMargin)

    testFix(program, expected, annotation)
  }

  def testNestedCaseClasses(): Unit = {
    val program =
      s"""
         |abstract class List
         |case class Node (value: Int, next: List) extends List
         |object Empty extends List
         |
         |${START}new$END Node(1, new Node(2, Empty))
          """.stripMargin

    check(program)

    val expected =
      s"""
         |abstract class List
         |case class Node (value: Int, next: List) extends List
         |object Empty extends List
         |
         |Node(1, new Node(2, Empty))
          """.stripMargin

    testFix(program, expected, annotation)
  }

  def testNestedCaseClassesWithNewNested(): Unit = {
    val program =
      s"""
         |abstract class List
         |case class Node (value: Int, next: List) extends List
         |object Empty extends List
         |
         |Node(1, ${START}new$END Node(2, Empty))
          """.stripMargin

    check(program)

    val expected =
      s"""
         |abstract class List
         |case class Node (value: Int, next: List) extends List
         |object Empty extends List
         |
         |Node(1, Node(2, Empty))
          """.stripMargin

    testFix(program, expected, annotation)
  }

  def testOverriddenApplyMethodHasNoErrors(): Unit = checkTextHasNoErrors(
    s"""
       |case class C(a: Int, x: String = "default")
       |object C { def apply(a: Int): C = C(a, "xxx") }
       |
         |${START}new$END C(0)
       """.stripMargin)

  def testCaseClassWithNormalClassNestedHasNoErrors(): Unit = checkTextHasNoErrors(
    s"""
       |class B()
       |case class A(b: B)
       |
       |A(new B())
     """.stripMargin)

  def testSimpleNormalClassHasNoErrors(): Unit = checkTextHasNoErrors(
    s"""class A(x: Int)
        |val a = new A(5)
     """.stripMargin)

  def testCreationWithMixinTraitHasNoErrors(): Unit = checkTextHasNoErrors(
    s"""
       |case class A()
       |
       |trait B {
       |  def method: Int = 1
       |}
       |
       |val a = new A() with B
     """.stripMargin
  )

  def testAnonymousCaseClassCreationHasNoErrors(): Unit = checkTextHasNoErrors(
    s"""
       |absract case class A() {
       |  def b(): Int
       |}
       |
       |val a = new A {
       |  override def b: Int = 2
       |}
     """.stripMargin
  )

  def testTraitCreationHasNoErrors(): Unit = checkTextHasNoErrors(
    s"""
       |trait A() {
       |  def b(): Int
       |}
       |
       |val a = new A {
       |  override def b: Int = 2
       |}
     """.stripMargin
  )

  def testCallingNonApplyMethodHasNoErrors(): Unit = checkTextHasNoErrors(
    s"""
       |case class Foo(x: Int, y: Int) {
       |def this(x: Int) = this(x, 0)
       |}
       |
       |val a = new Foo(1)""")

  def testShouldNotShowIfProblemsExistInConstructorCall(): Unit = checkTextHasNoErrors(
    s"""
       |case class Foo(x: Long)
       |val a = new Foo("z")
     """.stripMargin
  )

  def testShouldNotShowIfNoArgumentListInCall(): Unit = checkTextHasNoErrors(
    s"""
       |case class Foo()
       |val f = new Foo
     """.stripMargin
  )

  def testShouldNotShowIfCallingTypeAlias(): Unit = checkTextHasNoErrors(
    s"""
       |case class Foo()
       |type A = Foo
       |val f = new A()
     """.stripMargin
  )

  //SCL-10289
  def testShouldNotShowInApplyOfImplicitParameter(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  def apply(s: String)(implicit ev: Boolean): A = new A(s.toLowerCase)
       |}
       |case class A(s: String)
       |
       |new A("Hello")
     """.stripMargin)

  //SCL-10289
  def testShouldNotShowWithImplicitly(): Unit = checkTextHasNoErrors(
    s"""
       |trait ToLong[A] {
       |    def apply(a: A): Long
       |  }
       |
       |case class Container(a: Long)
       |
       |object Container {
       |  def apply[A: ToLong](a: A) = new Container(implicitly[ToLong[A]].apply(a))
       |}
       |
       |val c1 = Container(1)
     """.stripMargin)

  //SCL-10287
  def testShouldNotShowInCreationOfAnonymousClassInApply(): Unit = checkTextHasNoErrors(
    s"""
       |abstract case class A private[A] (s: String, i: Int)
       |object A {
       |def apply(s: String, i: Int): A =
       |new A(s.toUpperCase, i) { /* something*/ }
       |}""".stripMargin)

  def testAnonymousClass(): Unit = checkTextHasNoErrors(
    s"""case class X(a: Int) {
       |  def f = a
       |}
       |
       |new X(5) {
       |  override def f = 2
       |}
     """.stripMargin
  )

}
