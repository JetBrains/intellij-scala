package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class OverridingAnnotatorTest extends ScalaLightCodeInsightFixtureTestCase
  with ScalaHighlightingTestLike{

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  override protected def assertMessagesText(@Language("Scala 3") code: String, messagesConcatenated: String): Unit =
    super.assertMessagesText(code, messagesConcatenated)

  override protected def assertNoErrors(@Language("Scala 3") code: String): Unit =
    super.assertNoErrors(code)

  def testSyntheticUnapply(): Unit =
    assertNoErrors(
      """trait Test {
        |  trait Tree
        |  trait Name
        |  abstract class SelectExtractor {
        |    def apply(qualifier: Tree, name: Name): Select
        |    def unapply(select: Select): Option[(Tree, Name)]
        |  }
        |  case class Select(qualifier: Tree, name: Name)
        |    extends Tree {
        |  }
        |  object Select extends SelectExtractor {} // object creation impossible, unapply not defined...
        |
        |  def test(t: Tree) = t match {
        |    case Select(a, b) => // cannot resolve extractor
        |  }
        |}
        |""".stripMargin
    )

  def testPrivateVal(): Unit =
    assertNoErrors(
      """
        |object ppp {
        |class Base {
        |  private val something = 5
        |}
        |
        |class Derived extends Base {
        |  private val something = 8
        |}
        |}
        |""".stripMargin
    )

  def testClassParameter(): Unit =
    assertNoErrors(
      """
        |object ppp {
        |class A(x: Int)
        |class B(val x: Int) extends A(x)
        |case class C(x: Int) extends A(x)
        |}
        |""".stripMargin
    )

  def testVal(): Unit = {
    assertMessagesText(
      """
        |object ppp {
        |class Base {
        |  val something = 5
        |}
        |
        |class Derived extends Base {
        |  val something = 8
        |}
        |}
        |""".stripMargin,
      """Error(something,Value 'something' needs override modifier)"""
    )
  }

  def testNotConcreteMember(): Unit =
    assertNoErrors(
      """
        |object ppp {
        |class Base {
        |  def foo() = 1
        |}
        |
        |abstract class Derived extends Base {
        |  def foo(): Int
        |}
        |}
        |""".stripMargin
    )

  def testOverrideFinalMethod(): Unit = {
    assertMessagesText(
      """
        |object ppp {
        | class Base {
        |   final def foo() = 1
        | }
        |
        | class Derived extends Base {
        |   override def foo() = 2
        | }
        |}
        |""".stripMargin,
      """Error(foo,Method 'foo' cannot override final member)"""
    )
  }

  def testOverrideFinalVal(): Unit = {
    assertMessagesText(
      """
        |object ppp {
        | class Base {
        |   final val foo: Int = 1
        | }
        |
        | class Derived extends Base {
        |   override val foo: Int = 2
        | }
        |}
        |""".stripMargin,
      """Error(foo,Value 'foo' cannot override final member)"""
    )
  }

  def testOverrideFinalVar(): Unit = {
    assertMessagesText(
      """
        |object ppp {
        | class Base {
        |   final var foo = 1
        | }
        |
        | class Derived extends Base {
        |   override var foo = 2
        | }
        |}
        |""".stripMargin,
      """Error(foo,Mutable variable cannot be overridden)
        |Error(foo,Variable 'foo' cannot override final member)
        |""".stripMargin
    )
  }

  def testOverrideFinalAlias(): Unit = {
    assertMessagesText(
      """
        |object ppp {
        | class Base {
        |   final type foo = Int
        | }
        |
        | class Derived extends Base {
        |   override type foo = String
        | }
        |}
        |""".stripMargin,
      """Error(foo,Type 'foo' cannot override final member)"""
    )
  }

  //SCL-3258
  def testOverrideVarWithFunctions(): Unit = {
    assertNoErrors(
      """
        |object ppp {
        |abstract class Parent {
        |  var id: Int
        |}
        |
        |class Child extends Parent {
        |  def id = 0
        |  def id_=(v: Int): Unit = {
        |  }
        |}
        |}
        |""".stripMargin
    )
  }

  //SCL-4036
  def testDefOverrideValVar(): Unit = {
    assertMessagesText(
      """
        |object ppp {
        |abstract class A(val oof: Int = 42) {
        |  val foo = 42
        |  val afoo: Int
        |}
        |
        |class B extends A {
        |  override def foo = 999
        |  override def oof = 999
        |  override def afoo = 999
        |}
        |}
        |""".stripMargin,
      """Error(foo,method foo needs to be a stable, immutable value)
        |Error(oof,method oof needs to be a stable, immutable value)
        |Error(afoo,method afoo needs to be a stable, immutable value)
        |""".stripMargin
    )
  }

  def testScl6729(): Unit =
    assertNoErrors(
      """
        |trait Foo
        |
        |trait Bar {
        |  def foo: Foo = _
        |}
        |
        |class Baz extends Bar {
        |  override object foo extends Foo
        |}
        |""".stripMargin
    )

  //SCL-9578
  def testVarOverridesVal(): Unit = {
    assertMessagesText(
      """object ppp {
        |  trait A {
        |    val foo = 42
        |    val bar = 24
        |    val abar: Int
        |  }
        |
        |  class B(override var bar: Int) extends A {
        |    override var foo = 999
        |    var abar = 999
        |  }
        |}
        |""".stripMargin,
      """Error(bar,Mutable variable cannot be overridden)
        |Error(bar,variable bar cannot override immutable value)
        |Error(foo,Mutable variable cannot be overridden)
        |Error(foo,variable foo cannot override immutable value)
        |Error(abar,variable abar cannot override immutable value)
        |""".stripMargin
    )
  }

  //SCL-13039
  def testSCL13039(): Unit = {
    assertNoErrors(
      """
        |trait Test[T] {
        |  def foo[S](x: T): Unit = {
        |    val t = new Test[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |  }
        |}
        |""".stripMargin
    )
  }

  def testSCL13039_1(): Unit = {
    assertNoErrors(
      """
        |trait Test2[T] {
        |  def foo[S](x: T): Unit = {
        |//    val t = new Test2[S] {
        |//      override def foo[U](x: S): Unit = {}
        |//    }
        |  }
        |  def other[S](x: T): Unit = {
        |    val t = new Test2[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |  }
        |}
        |""".stripMargin
    )
  }

  def testScl11327(): Unit =
    assertNoErrors(
      """import MyOverride._
        |
        |class MyOverride(string: String) {
        |
        |  def foo(): String = {
        |    bar(string)
        |  }
        |}
        |
        |object MyOverride extends SomeTrait {
        |  def bar(string: String): String = string + "bar"
        |
        |  override def baz(string: String): String = string.reverse + "baz"
        |}
        |
        |trait SomeTrait {
        |  def baz(string: String): String
        |}
        |""".stripMargin
    )

  def testScl9767(): Unit =
    assertNoErrors(
      """case class Q[B](b: B)
        |
        |trait Foo[A] {
        |  def method(value: A): Unit
        |
        |  def concat[T](that: Foo[T]): Foo[Q[A]] = new Foo[Q[A]] {
        |    override def method(value: Q[A]): Unit = ()
        |  }
        |}
        |""".stripMargin
    )

  def testDependentParamType(): Unit =
    assertNoErrors(
      """
        |class A {
        |  class B
        |}
        |trait Base {
        |  def foo(a: A)(b: a.B): Unit
        |}
        |trait Impl extends Base {
        |  override def foo(a: A)(b: a.B): Unit = {}
        |}
        |""".stripMargin
    )

  def testScl12401(): Unit =
    assertNoErrors(
      """trait Callback {
        |  def run(): Unit
        |}
        |
        |class Target {
        |  private[this] var callback: Callback = new Callback {
        |    override def run(): Unit = {}
        |  }
        |
        |  def setCallback(x: Callback): Target = {
        |    callback = x
        |    this
        |  }
        |
        |  def run(): Unit = callback.run()
        |}
        |
        |object Pimps {
        |
        |  implicit class TargetPimps(t: Target) {
        |    def setCallback(callback: => Unit): Target = t.setCallback(new Callback {
        |      override def run(): Unit = callback
        |    })
        |  }
        |
        |}
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    import Pimps._
        |    val target = (new Target).setCallback {
        |      println("Hello from callback!")
        |    } // <- Here I am getting "Expression of type Unit doesn't conform to expected type Callback"
        |    target.run()
        |  }
        |}
        |""".stripMargin
    )

  def testScl13265(): Unit =
    assertNoErrors(
      """trait Foo {
        |  type T
        |}
        |
        |trait Bar {
        |  def apply(foo: Foo)(t: foo.T): Unit
        |}
        |
        |class BarImpl extends Bar {
        |  def apply(foo: Foo)(t: foo.T): Unit = Unit
        |}
        |""".stripMargin
    )

  def testScl14152(): Unit =
    assertNoErrors(
      """sealed trait TagExpr
        |
        |object TagExpr {
        |
        |  sealed trait Composite extends TagExpr {
        |    def head: TagExpr
        |    def tail: Seq[TagExpr]
        |  }
        |
        |  final case class And(head: TagExpr, tail: TagExpr*) extends Composite
        |}
        |""".stripMargin
    )

  def testSCL14570(): Unit =
    assertNoErrors(
      """trait WeaveResource {
        |  def url(): String
        |}
        |
        |case class DefaultWeaveResource(url: String) extends WeaveResource
        |""".stripMargin
    )

  def testSCL14570_2(): Unit =
    assertNoErrors(
      """trait WeaveResource {
        |  def url(): Seq[String]
        |}
        |
        |case class DefaultWeaveResource(url: String*) extends WeaveResource
        |""".stripMargin
    )

  def testSCL17459(): Unit =
    assertNoErrors(
      """trait Api {
        |  trait Reader[T]
        |  def OptionReader[T](implicit ev: Reader[T]): Reader[Option[T]] = ???
        |}
        |object MyApi extends Api {
        |  override def OptionReader[T: Reader]: Reader[Option[T]] = ???   // <- error highlighted here
        |}""".stripMargin
    )

  def testSCL17595(): Unit = assertNoErrors(
    """
      |trait A[Z] {
      |  def bar(): Unit
      |}
      |trait B {
      |  self: A[_] =>
      |  override def bar(): Unit = { println("B.bar")}  // <<-- IJ is unhappy about `override` here
      |}
      |""".stripMargin
  )

  def testSCL8228(): Unit =
    assertNoErrors(
      """trait TraitWithGeneric [T]{
        |  // If you click on the left on green down arrow, it does not list implementation from SelfTypeWildcard
        |  def method: String
        |}
        |
        |trait SelfType { self: TraitWithGeneric[Unit] =>
        |  override def method = "no problem here"
        |}
        |
        |trait SelfTypeWildcard { self: TraitWithGeneric[_] =>
        |  // BUG: Triggers "Overrides nothing" inspection
        |  override def method = "inspection problem here for selftype"
        |}
        |object ItActuallyCompilesAndWorks extends TraitWithGeneric[Unit] with SelfTypeWildcard
        |ItActuallyCompilesAndWorks.method // returns "inspection problem here for selftype"
        |""".stripMargin
    )

  def testSCL7987(): Unit =
    assertNoErrors(
      """
        |trait Foo {
        |  protected type T
        |  def foo(t: T): Unit
        |}
        |
        |new Foo {
        |  override protected type T = String // will pass if protected modifier is removed
        |  override def foo(t: T) = ()
        |}
        |""".stripMargin
    )

  //FIXME (SCL-8577) overriding of inaccessible members with qualified private must show an error
  // There is a slight difference in Scala 2 compared to Scala 3.
  // In Scala 2, the foo2 should also be highlighted as an error, but it's not
  def testInaccessiblePrivateMembers(): Unit = {
    assertErrorsText(
      """package aaa {
        |  class A {
        |    private def foo0: Int = 1
        |    private[this] def foo1: Int = 1
        |    private[A] def foo2: Int = 1
        |    private[aaa] def foo3: Int = 1
        |  }
        |}
        |
        |
        |package bbb {
        |  class B extends aaa.A {
        |    override def foo0: Int = 1
        |    override def foo1: Int = 1
        |    override def foo2: Int = 1
        |    override def foo3: Int = 1
        |  }
        |}
        |""".stripMargin,
      """Error(foo0,Method 'foo0' overrides nothing)
        |Error(foo1,Method 'foo1' overrides nothing)
        |""".stripMargin
    )
  }

  def testSCL6809(): Unit = {
    //FIXME: SCL-6809 (THERE SHOULD BE NO ERRORS)
    assertErrorsText(
      """import java.{util => ju}
        |
        |abstract class CollectionToArrayBug[E <: AnyRef](collection: ju.Collection[E])
        |  extends ju.Collection[E]
        |{
        |  def toArray[T](a: Array[T]): Array[T] = ???
        |  override def toArray[T](a: Array[T with AnyRef]): Array[T with AnyRef] = ???
        |}
        |""".stripMargin,
      """Error(toArray,Method 'toArray' overrides nothing)"""
    )
  }

  def testGivenInstances(): Unit = {
    assertMessagesText(
      """trait MyTrait
        |
        |class Base {
        |  given String = "42"
        |  given givenInt: Int = 42
        |  given givenStructured: MyTrait with {}
        |}
        |
        |class Child extends Base {
        |  override given String = "23"
        |  override given givenInt: Int = 23
        |  override given givenStructured: MyTrait with {}
        |}
        |""".stripMargin,
      """Error(String,Method 'given_String' cannot override final member)
        |Error(givenInt,Method 'givenInt' cannot override final member)
        |Error(override,'override' modifier allowed only for type definitions members)
        |""".stripMargin
    )
  }

  def testSCL24536(): Unit = {
    assertNoErrors(
      """
        |trait Test[T] {
        |  def foo[S](x: T): Unit = {
        |    new Test[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |
        |    class InnerClass extends Test[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |  }
        |
        |  def other[S](x: T): Unit = {
        |    new Test[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |
        |    class InnerClass extends Test[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |  }
        |}
        |""".stripMargin
    )
  }

  def testSCL20442(): Unit = assertNoErrors(
    """
      |import java.util
      |
      |trait BaseScala {
      |  def foo1(result: util.Collection[ScalaInterface]): Unit
      |  def foo2(result: util.Collection[_ >: ScalaInterface]): Unit
      |  def foo3(result: util.Collection[ScalaInterfaceTyped[_]]): Unit
      |  def foo4(result: util.Collection[_ >: ScalaInterfaceTyped[_]]): Unit
      |  def foo5(result: ScalaInterfaceTyped[_ >: ScalaInterfaceTyped[_]]): Unit
      |}
      |
      |trait ScalaInterface {}
      |trait ScalaInterfaceTyped[T] {}
      |
      |class ChildOfScala extends BaseScala {
      |  override def foo1(result: util.Collection[ScalaInterface]): Unit = ()
      |  override def foo2(result: util.Collection[_ >: ScalaInterface]): Unit = ()
      |  override def foo3(result: util.Collection[ScalaInterfaceTyped[_]]): Unit = ()
      |  override def foo4(result: util.Collection[_ >: ScalaInterfaceTyped[_]]): Unit = ()
      |  override def foo5(result: ScalaInterfaceTyped[_ >: ScalaInterfaceTyped[_]]): Unit = ()
      |}
      |""".stripMargin
  )
}
