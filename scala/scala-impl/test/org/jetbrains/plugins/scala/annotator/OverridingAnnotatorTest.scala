package org.jetbrains.plugins.scala.annotator

/**
  * User: Alexander Podkhalyuzin
  * Date: 30.01.12
  */

class OverridingAnnotatorTest extends OverridingAnnotatorTestBase {

  def testSyntheticUnapply(): Unit = {
    assertMatches(messages(
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
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testPrivateVal(): Unit = {
    assertMatches(messages(
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
      """.stripMargin)) {
      case Nil =>
    }
  }

  def testClassParameter(): Unit = {
    assertMatches(messages(
      """
        |object ppp {
        |class A(x: Int)
        |class B(val x: Int) extends A(x)
        |case class C(x: Int) extends A(x)
        |}
      """.stripMargin)) {
      case Nil =>
    }
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
    assertMatches(messages(
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
      """.stripMargin)) {
      case Nil =>
    }
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
    assertMatches(messages(code)) {
      case Nil =>
    }
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
    assertMatches(messages(code)) {
      case Nil =>
    }
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
    assertMatches(messages(code)) {
      case Nil =>
    }
  }

}
