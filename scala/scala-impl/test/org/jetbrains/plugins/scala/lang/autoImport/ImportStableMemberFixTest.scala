package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.annotator.intention.ScalaImportGlobalMemberFix
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
}
