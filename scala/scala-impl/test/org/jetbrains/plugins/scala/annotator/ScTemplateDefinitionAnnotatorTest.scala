package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Message.Error
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestFixture
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScTemplateDefinitionAnnotatorTest
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaHighlightingTestLike {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testTraitPassingConstructorParameters(): Unit =
    assertMessagesText(
      """
        |trait Foo(x: Int)
        |trait Bar extends Foo(123)
        |
        |trait Baz
        |trait Qux extends Baz with Foo(123)
        |""".stripMargin,
      """Error(Foo(123),Trait Bar may not call constructor of Foo)
        |Error(Foo(123),Trait Qux may not call constructor of Foo)
        |""".stripMargin
    )

  def testDontCallConstructorTwice(): Unit =
    assertMessagesText(
      """trait Foo(x: Int)
        |class F extends Foo(123)
        |case class Bar() extends F with Foo
        |""".stripMargin,
      //TODO: remove when SCL-23134 is fixed
      """Error(Foo,Unspecified value parameters: x: Int)""".stripMargin
    )

  def testDontCallConstructorTwice_1(): Unit =
    assertMessagesText(
      """trait Foo(x: Int)
        |class F extends Foo(1)
        |class Baz extends F with Foo
        |""".stripMargin,
      //TODO: remove when SCL-23134 is fixed
      """Error(Foo,Unspecified value parameters: x: Int)""".stripMargin
    )

  def testIndirectImplementation(): Unit =
    assertMessagesText(
      """trait Greeting(val name: String)
        |trait FormalGreeting extends Greeting
        |class E extends FormalGreeting
        |""".stripMargin,
      """Error(E,Parameterized trait Greeting is indirectly implemented, needs to be implemented directly so that arguments can be passed)
        |""".stripMargin
    )

  def testSCL21122(): Unit =
    assertNoErrors(
      """object TraitWithDefaultParams {
        |  trait IFoo(x: String = "foo") {
        |    val name = x
        |  }
        |
        |  class Foo extends IFoo // compile passed, but idea reports error
        |}
        |""".stripMargin
    )

  //SCL-22565
  def testAnonymousClassWithoutBaseClassWithExpectedType(): Unit = {
    assertNoErrors(
      """trait Test {
        |  def foo[A](value: A): Unit
        |}
        |
        |object Live {
        |  //expected type 1
        |  def live: Test = new {
        |    def foo[A](value: A): Unit = ()
        |    def extra: Int = 0
        |  }
        |
        |  //expected type 2
        |  def foo(test: Test): Unit = ()
        |  foo(new {
        |    def foo[A](value: A): Unit = ()
        |    def extra: Int = 0
        |  })
        |}
        |""".stripMargin
    )
  }

  //SCL-22565
  def testAnonymousClassWithoutBaseClassWithExpectedType_MethodsWithMissingImplementation(): Unit = {
    assertMessagesText(
      """trait Test {
        |  def foo[A](value: A): Unit
        |  def bar[A](value: A): Unit
        |}
        |
        |object Live {
        |  //expected type 1
        |  def live: Test = new {
        |    def foo[A](value: A): Unit = ()
        |  }
        |
        |  //expected type 2
        |  def foo(test: Test): Unit = ()
        |  foo(new {
        |    def foo[A](value: A): Unit = ()
        |  })
        |}
        |""".stripMargin,
      """Error(new,Object creation is impossible since member bar[A](value: A): Unit in Test is not defined)
        |Error(new,Object creation is impossible since member bar[A](value: A): Unit in Test is not defined)
        |""".stripMargin
    )

    val objectCreationImpossible = "Object creation is impossible"
    val quickFixFixture = new ScalaQuickFixTestFixture(myFixture, objectCreationImpossible, trimExpectedText = false)
    quickFixFixture.descriptionMatcher = _.contains(objectCreationImpossible)

    val quickFixes = quickFixFixture.findMatchingHighlights().flatMap(ScalaQuickFixTestFixture.findRegisteredQuickFixes)
    quickFixFixture.applyQuickFixesAndCheckExpected(
      quickFixes,
      """trait Test {
        |  def foo[A](value: A): Unit
        |  def bar[A](value: A): Unit
        |}
        |
        |object Live {
        |  //expected type 1
        |  def live: Test = new {
        |    def foo[A](value: A): Unit = ()
        |
        |    override def bar[A](value: A): Unit = ???
        |  }
        |
        |  //expected type 2
        |  def foo(test: Test): Unit = ()
        |  foo(new {
        |    def foo[A](value: A): Unit = ()
        |
        |    override def bar[A](value: A): Unit = ???
        |  })
        |}
        |""".stripMargin
    )
  }
}

@Category(Array(classOf[TypecheckerTests]))
class ScTemplateDefinitionAnnotatorTest3_6
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaHighlightingTestLike {


  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6

  private def errorMessage(targetType: String, givenName: String, traitName: String): String =
    ScalaBundle.message("no.given.instance.for.deferred", targetType, givenName, traitName)

  def testTraitDeferredGivenSimple(): Unit =
    assertMessages(
      """
        |trait Ord[A]
        |trait Foo {
        |  given ordInt: Ord[Int] = scala.compiletime.deferred
        |}
        |
        |object FooImpl extends Foo
        |""".stripMargin,
      Error("object FooImpl extends Foo", errorMessage("Ord[Int]", "ordInt", "Foo"))
    )

  def testTraitDeferredGivenAlias(): Unit =
    assertMessages(
      """
        |trait Ord[A]
        |trait Foo {
        |  type Self : Ord as ord
        |}
        |
        |object FooImpl extends Foo {
        |  type Self = String
        |}
        |""".stripMargin,
      Error("object FooImpl extends Foo", errorMessage("Ord[FooImpl.Self]", "ord", "Foo"))
    )

  def testDeferredGivenWithEv(): Unit =
    assertNoMessages(
      """trait Ord[A]
        |trait Foo {
        |  type Self : Ord
        |}
        |
        |class FooImpl[A](using Ord[A]) extends Foo {
        |  type Self = A
        |}
        |""".stripMargin
    )

  def testDeferredGivenWithEv2(): Unit =
    assertNoMessages(
      """
        |trait Show[A]
        |trait Foo {
        |  type Elem
        |  given Show[Elem] = scala.compiletime.deferred
        |}
        |
        |given Show[String] = ???
        |class FooImpl extends Foo {
        |  type Elem = String
        |}
        |""".stripMargin
    )

  def testNoImplicitSearchInClassContext(): Unit =
    assertMessages(
      """trait Show[A]
        |trait Foo {
        |  type Elem
        |  given Show[Elem] = scala.compiletime.deferred
        |}
        |
        |class FooImpl extends Foo {
        |  given Show[String] = ???
        |  type Elem = String
        |}
        |""".stripMargin,
      Error("class FooImpl extends Foo", errorMessage("Show[FooImpl.this.Elem]", "given_Show_Elem", "Foo"))
    )

  def testExplicitOverride(): Unit =
    assertNoMessages(
      """trait Ord[A]
        |trait Foo {
        |  given ordInt: Ord[Int] = scala.compiletime.deferred
        |}
        |
        |object FooImpl extends Foo {
        |  override given ordInt: Ord[Int] = ???
        |}
        |""".stripMargin
    )

  def testOnlyShowErrorInSuper(): Unit =
    assertMessages(
      """
        |trait Ord[A]
        |trait Foo {
        |  given ordInt: Ord[Int] = scala.compiletime.deferred
        |}
        |
        |class Bar extends Foo
        |class Baz extends Bar
        |""".stripMargin,
      Error("class Bar extends Foo", errorMessage("Ord[Int]", "ordInt", "Foo"))
    )

  def testParameterized(): Unit =
    assertMessages(
      """
        |trait Ord[A]
        |trait Foo {
        |  given ordInt[A]: Ord[A] = scala.compiletime.deferred
        |}
        |
        |object FooImpl extends Foo
        |""".stripMargin,
      Error("object FooImpl extends Foo", ScalaBundle.message("parameterized.deferred.given", "ordInt", "Foo"))
    )
}