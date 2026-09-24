package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportGlobalMemberFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class ImportStableMemberFixTest extends ImportElementFixTestBase[ScReferenceExpression] {
  override def createFix(element: ScReferenceExpression) =
    ScalaImportGlobalMemberFix.fixWithoutPrefix(element)

  @Test
  def testNextInt(): Unit = checkElementsToImport(
    s"""
       |object Test {
       |  ${CARET}nextInt()
       |}
       |""".stripMargin,

    "scala.util.Random.nextInt"
  )

  @Test
  def testEmptyList(): Unit = checkElementsToImport(
    s"""
       |class Foo {
       |  ${CARET}emptyList
       |}
     """.stripMargin,

    "java.util.Collections.emptyList"
  )

  @Test
  def testConstant(): Unit = checkElementsToImport(
    s"""
       |class Test {
       |  ${CARET}PositiveInfinity
       |}
       |""".stripMargin,

    "scala.Double.PositiveInfinity",
    "scala.Float.PositiveInfinity",
  )

  @Test
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

  @Test
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

  @Test
  def testNoInstanceMethod(): Unit = checkNoImportFix(
    s"""
       |class A {
       |  def foo(): Int = 1
       |}
       |object Test {
       |  ${CARET}foo()
       |}
       |""".stripMargin)

  @Test
  def testTooManyCandidates(): Unit = checkNoImportFix(
    s"""
       |class Test {
       |  ${CARET}empty
       |}
       |""".stripMargin
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testExcludedMethod(): Unit = {
    withExcluded("scala.util.Random.nextInt") {
      checkNoImportFix(
        s"""object Test {
           |  ${CARET}nextInt()
           |}
           |""".stripMargin)
    }
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testJavaEnumConstant(): Unit = {
    myFixture.addFileToProject(
      "org/example/MyJavaEnum1.java",
      """package org.example;
        |
        |import java.time.Duration;
        |
        |public enum MyJavaEnum1 {
        |    SECONDS("Seconds", Duration.ofSeconds(1)),
        |    MINUTES("Minutes", Duration.ofSeconds(60)),
        |    HOURS("Hours", Duration.ofSeconds(3600)),
        |    ETERNITY("ETERNITY", Duration.ofSeconds(Integer.MAX_VALUE)),
        |    ;
        |
        |    MyJavaEnum1(String nanos, Duration duration) {
        |    }
        |}
        |""".stripMargin
    )

    checkElementsToImport(
      s"""import org.example.MyJavaEnum1
         |
         |object Example {
         |  def foo1(value: MyJavaEnum1): Unit = ???
         |
         |  foo1(HOURS)
         |  foo1(ETERNI${CARET}TY)
         |}
         |""".stripMargin,
      "org.example.MyJavaEnum1.ETERNITY"
    )
  }

}
