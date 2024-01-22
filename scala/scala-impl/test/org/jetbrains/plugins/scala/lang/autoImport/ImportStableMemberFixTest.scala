package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportGlobalMemberFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

class ImportStableMemberFixTest extends ImportElementFixTestBase[ScReferenceExpression] {
  override def createFix(element: ScReferenceExpression) =
    ScalaImportGlobalMemberFix.fixWithoutPrefix(element)

  def testNextInt(): Unit = checkElementsToImport(
    s"""
       |object Test {
       |  ${CARET}nextInt()
       |}
       |""".stripMargin,

    "scala.util.Random.nextInt"
  )

  def testEmptyList(): Unit = checkElementsToImport(
    s"""
       |class Foo {
       |  ${CARET}emptyList
       |}
     """.stripMargin,

    "java.util.Collections.emptyList"
  )

  def testConstant(): Unit = checkElementsToImport(
    s"""
       |class Test {
       |  ${CARET}PositiveInfinity
       |}
       |""".stripMargin,

    "scala.Double.PositiveInfinity",
    "scala.Float.PositiveInfinity",
  )

  def testConstantAsPattern(): Unit = doTest(
    fileText =
      s"""
         |class Test {
         |  0.0 match {
         |    case ${CARET}PositiveInfinity =>
         |  }
         |}
         |""".stripMargin,
    expectedText =
      """
        |import scala.Double.PositiveInfinity
        |
        |class Test {
        |  0.0 match {
        |    case PositiveInfinity =>
        |  }
        |}
        |""".stripMargin,

    selected = "scala.Double.PositiveInfinity"
  )

  def testNoPrivateMethod(): Unit = checkNoImportFix(
    s"""
       |object A {
       |  private def myFoo(): Unit = ???
       |}
       |
       |object Test {
       |  ${CARET}myFoo()
       |}
       |""".stripMargin)

  def testNoInstanceMethod(): Unit = checkNoImportFix(
    s"""
       |class A {
       |  def foo(): Int = 1
       |}
       |object Test {
       |  ${CARET}foo()
       |}
       |""".stripMargin)

  def testTooManyCandidates(): Unit = checkNoImportFix(
    s"""
       |class Test {
       |  ${CARET}empty
       |}
       |""".stripMargin
  )

  def testViaInheritors(): Unit = checkElementsToImport(
    s"""trait Base[T] {
       |  def foo(t: T): Int = 1
       |}
       |class A[T] extends Base[T]
       |object B extends A[Int]
       |
       |object C extends Base[Long]
       |
       |object Test {
       |  ${CARET}foo()
       |}
       |""".stripMargin,

    "B.foo", "C.foo"
  )

  def testSingleOptionIfNonParameterizedInheritance(): Unit = checkElementsToImport(
    s"""
       |trait Base {
       |  def foo(): Int = 1
       |}
       |class A extends Base
       |object B extends A
       |
       |object C extends Base
       |
       |object Test {
       |  ${CARET}foo()
       |}
       |""".stripMargin,

    "B.foo"
  )

  def testCompanionObjectValue(): Unit = doTest(
    fileText =
      s"""
         |trait Foo {
         |  ${CARET}foo
         |}
         |
         |object Foo {
         |  val (_, foo) = ???
         |}""".stripMargin,
    expectedText =
      s"""
         |import Foo.foo
         |
         |trait Foo {
         |  foo
         |}
         |
         |object Foo {
         |  val (_, foo) = ???
         |}""".stripMargin,

    selected = "Foo.foo"
  )

  def testCompanionObjectMethod(): Unit = doTest(
    fileText =
      s"""
         |class Foo {
         |  ${CARET}foo
         |}
         |
         |object Foo {
         |  def foo(): Unit = {}
         |}
         |""".stripMargin,
    expectedText =
      """
        |import Foo.foo
        |
        |class Foo {
        |  foo
        |}
        |
        |object Foo {
        |  def foo(): Unit = {}
        |}""".stripMargin,
    selected = "Foo.foo"
  )

  def testPrivateThisCompanionObjectMethod(): Unit = checkNoImportFix(
    s"""
       |class Foo {
       |  ${CARET}foo
       |}
       |
       |object Foo {
       |  private[this] def foo(): Unit = {}
       |}""".stripMargin
  )

  def testPrivateCompanionObjectMethod(): Unit = checkElementsToImport(
    s"""
       |class Foo {
       |  ${CARET}foo
       |}
       |
       |object Foo {
       |  private def foo(): Unit = {}
       |}""".stripMargin,

    "Foo.foo"
  )

  def testMethodFromVal(): Unit = checkElementsToImport(
    s"""
       |trait MyMethods {
       |  def myMethod(): Unit = ???
       |}
       |
       |trait Abc {
       |  trait API extends MyMethods
       |  val api: API
       |}
       |
       |trait Abc2 extends Abc {
       |  trait API extends super.API
       |  val api: API = ???
       |}
       |
       |object AbcImpl extends Abc2
       |
       |object Test {
       |  ${CARET}myMethod()
       |}""".stripMargin,

    "AbcImpl.api.myMethod"
  )

  def testExcludedClass(): Unit = {
    withExcluded("scala.util.Random") {
      checkNoImportFix(
        s"""object Test {
           |  ${CARET}nextInt()
           |}
           |""".stripMargin)
      checkNoImportFix(
        s"""object Test {
           |  ${CARET}Random.nextInt()
           |}
           |""".stripMargin)
    }
  }

  def testExcludedMethod(): Unit = {
    withExcluded("scala.util.Random.nextInt") {
      checkNoImportFix(
        s"""object Test {
           |  ${CARET}nextInt()
           |}
           |""".stripMargin)
    }
  }

  def testInheritedMethodFromTrait(): Unit = checkElementsToImport(
    s"""trait MyHelperTrait {
       |  def defInTrait: String = ???
       |}
       |
       |object MyObject extends MyHelperTrait
       |
       |class Example {
       |  println(defIn${CARET}Trait)
       |}
       |""".stripMargin,
    "MyObject.defInTrait"
  )

  def testInheritedMethodFromClass(): Unit = checkElementsToImport(
    s"""class MyHelperClass {
       |  def defInClass: String = ???
       |}
       |
       |object MyObject extends MyHelperClass
       |
       |class Example {
       |  println(defIn${CARET}Class)
       |}
       |""".stripMargin,
    "MyObject.defInClass"
  )

  def testInheritedValFromTrait(): Unit = checkElementsToImport(
    s"""trait MyHelperTrait {
       |  val valInTrait: String = ???
       |}
       |
       |object MyObject extends MyHelperTrait
       |
       |class Example {
       |  println(valIn${CARET}Trait)
       |}
       |""".stripMargin,
    "MyObject.valInTrait"
  )

  def testInheritedValFromClass(): Unit = checkElementsToImport(
    s"""class MyHelperClass {
       |  val valInClass: String = ???
       |}
       |
       |object MyObject extends MyHelperClass
       |
       |class Example {
       |  println(valIn${CARET}Class)
       |}
       |""".stripMargin,
    "MyObject.valInClass"
  )

}
