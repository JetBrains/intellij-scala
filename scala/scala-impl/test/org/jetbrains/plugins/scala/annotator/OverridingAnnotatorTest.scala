package org.jetbrains.plugins.scala.annotator

/**
  * User: Alexander Podkhalyuzin
  * Date: 30.01.12
  */

class OverridingAnnotatorTest extends OverridingAnnotatorTestBase {

  def testSyntheticUnapply(): Unit = {
    assertNothing(messages(
      """
        |trait Test {
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
      """.stripMargin))
  }

  def testPrivateVal(): Unit = {
    assertNothing(messages(
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
      """.stripMargin))
  }

  def testClassParameter(): Unit = {
    assertNothing(messages(
      """
        |object ppp {
        |class A(x: Int)
        |class B(val x: Int) extends A(x)
        |case class C(x: Int) extends A(x)
        |}
      """.stripMargin))
  }

  def testVal(): Unit = {
    assertMatches(messages(
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
      """.stripMargin)) {
      case List(Error("something", "Value 'something' needs override modifier")) =>
    }
  }

  def testNotConcreteMember(): Unit = {
    assertNothing(messages(
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
      """.stripMargin))
  }

  def testOverrideFinalMethod(): Unit = {
    assertMatches(messages(
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
      """.stripMargin)) {
      case List(Error("foo", "Method 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalVal(): Unit = {
    assertMatches(messages(
      """
        |object ppp {
        | class Base {
        |   final val foo = 1
        | }
        |
        | class Derived extends Base {
        |   override val foo = 2
        | }
        |}
      """.stripMargin)) {
      case List(Error("foo", "Value 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalVar(): Unit = {
    assertMatches(messages(
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
      """.stripMargin)) {
      case List(Error("foo", "Variable 'foo' cannot override final member")) =>
    }
  }

  def testOverrideFinalAlias(): Unit = {
    assertMatches(messages(
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
      """.stripMargin)) {
      case List(Error("foo", "Type 'foo' cannot override final member")) =>
    }
  }

  //SCL-3258
  def testOverrideVarWithFunctions(): Unit = {
    val code =
      """
        |object ppp {
        |abstract class Parent {
        |  var id: Int
        |}
        |
        |class Child extends Parent {
        |  def id = 0
        |  def id_=(v: Int) {
        |  }
        |}
        |}
      """.stripMargin
    assertNothing(messages(code))
  }

  //SCL-4036
  def testDefOverrideValVar(): Unit = {
    val code =
      """
        |object ppp {
        |abstract class A(val oof: Int = 42, var rab: Int = 24) {
        |  val foo = 42
        |  var bar = 24
        |  val afoo: Int
        |  var abar: Int
        |}
        |
        |class B extends A {
        |  override def foo = 999
        |  override def bar = 1000
        |  override def oof = 999
        |  override def rab = 999
        |  override def afoo = 999
        |  override def abar = 999
        |}
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case List(Error("foo", "method foo needs to be a stable, immutable value"),
      Error("bar", "method bar cannot override a mutable variable"),
      Error("oof", "method oof needs to be a stable, immutable value"),
      Error("rab", "method rab cannot override a mutable variable"),
      Error("afoo", "method afoo needs to be a stable, immutable value")) =>
    }
  }

  def testScl6729(): Unit = {
    assertNothing(
      messages(
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
        """.stripMargin)
    )
  }

  //SCL-9578
  def testVarOverridesVal(): Unit = {
    val code =
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
        |
        |}
      """.stripMargin
    assertMatches(messages(code)) {
      case List(
      Error("bar", "variable bar cannot override immutable value"),
      Error("foo", "variable foo cannot override immutable value"),
      Error("abar", "variable abar cannot override immutable value")) =>
    }
  }

  def testSCL13039(): Unit = {
    val code =
      """
        |trait Test[T] {
        |  def foo[S](x: T): Unit = {
        |    val t = new Test[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |  }
        |}
      """.stripMargin
    assertNothing(messages(code))
  }

  def testSCL13039_1(): Unit = {
    val code =
      """
        |trait Test[T] {
        |  def foo[S](x: T): Unit = {
        |//    val t = new Test[S] {
        |//      override def foo[U](x: S): Unit = {}
        |//    }
        |  }
        |  def other[S](x: T): Unit = {
        |    val t = new Test[S] {
        |      override def foo[U](x: S): Unit = {}
        |    }
        |  }
        |}
      """.stripMargin
    assertNothing(messages(code))
  }

  def testScl11327(): Unit = {
    assertNothing(
      messages(
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
        """.stripMargin))
  }

  def testScl9767(): Unit = {
    assertNothing(
      messages(
        """case class Q[B](b: B)
          |
          |trait Foo[A] {
          |  def method(value: A): Unit
          |
          |  def concat[T](that: Foo[T]): Foo[Q[A]] = new Foo[Q[A]] {
          |    override def method(value: Q[A]): Unit = ()
          |  }
          |}
        """.stripMargin))
  }

  def testDependentParamType(): Unit = {
    val code =
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
      """.stripMargin
    assertNothing(messages(code))
  }

  def testScl12401(): Unit = {
    val code =
      """
        |trait Callback {
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
      """.stripMargin
    assertNothing(messages(code))
  }

  def testScl13265(): Unit = {
    assertNothing(
      messages(
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
        """.stripMargin))
  }

  def testScl14152(): Unit = {
    assertNothing(
      messages(
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
        """.stripMargin))
  }

  def testSCL14570(): Unit = {
    assertNothing(messages(
      """trait WeaveResource {
        |  def url(): String
        |}
        |
        |case class DefaultWeaveResource(url: String) extends WeaveResource
      """.stripMargin
    ))
  }

  def testSCL14570_2(): Unit = {
    assertNothing(messages(
      """trait WeaveResource {
        |  def url(): Seq[String]
        |}
        |
        |case class DefaultWeaveResource(url: String*) extends WeaveResource
      """.stripMargin
    ))
  }

  def testSCL17459(): Unit =
    assertNothing(
      messages(
        """trait Api {
          |  trait Reader[T]
          |  def OptionReader[T](implicit ev: Reader[T]): Reader[Option[T]] = ???
          |}
          |object MyApi extends Api {
          |  override def OptionReader[T: Reader]: Reader[Option[T]] = ???   // <- error highlighted here
          |}""".stripMargin
      )
    )
}
