package org.jetbrains.plugins.scala.annotator

class OverrideHighlightingTest extends ScalaHighlightingTestBase {
  def testScl13051(): Unit = {
    val code =
      s"""
         |trait Base {
         |  def foo: Int = 42
         |}
         |
         |class AClass extends Base {
         |  override def foo: String = "42"
         |}
       """.stripMargin
    assertMatches(errorsFromScalaCode(code)) {
      case Error(_, "Overriding type String does not conform to base type Int") :: Nil =>
    }
  }

  def testScl13051_1(): Unit = {
    val code =
      s"""
         |trait T1 {
         |  val foo: T1
         |}
         |trait T2 extends T1 {
         |  override val foo: T2
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testScl13051_2(): Unit = {
    val code =
      s"""
         |trait Base {
         |  def foo(x: Int): Int = 42
         |  def foo(x: String): String = "42"
         |}
         |class AClass extends Base {
         |  override def foo(x: Int): Int = 42
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13051_3(): Unit = {
    val code =
      s"""
         |trait Base[c] {
         |  def foo: c
         |}
         |
         |class AClass[a] extends Base[a]{
         |  override val foo: a = ???
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13051Setter(): Unit = {
    val code =
      s"""
         |abstract class A() {
         |  var x: Int
         |}
         |
         |abstract class B() extends A() {
         |  var xx: Int = 0;
         |  def x: Int = xx
         |  def x_=(y: Int) = xx = y;
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13051replaceDesignators(): Unit = {
    val code =
      """
        |trait Eater {
        |  type Food[T]
        |}
        |
        |trait Fruit {
        |  type Seed
        |}
        |
        |trait PipExtractor {
        |  def extract(a: Fruit): a.Seed
        |}
        |
        |trait LaserGuidedPipExtractor extends PipExtractor {
        |  def extract(f: Fruit): f.Seed
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  private def addType(over: String): String = {
    val split = over.split("=")
    split(0) + ": Int =" + split(1)
  }

  private def inheritWithStringToIntConversion(base: String, over: String) =
    s"""
        |class Test {
        |  implicit def s2i(s: String): Int = s.length
        |  trait Base { $base }
        |  trait Sub extends Base { $over }
        |  trait Sub2 extends Base { ${addType(over)} }
        |}
      """.stripMargin

  def testValInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "val foo = \"\"")))
  }

  def testValInheritReturnTypeParens(): Unit = {
    //TODO: this, for some reason, does not work in compiler
    assertMatches(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "val foo = \"\""))) {
      case Error(_, "Overriding type String does not conform to base type () => Int") :: Nil =>
    }
  }

  def testFunInheritReturnTypeParens(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "def foo = \"\"")))
  }

  def testFunInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "def foo = \"\"")))
  }

  def testParensFunInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "def foo() = \"\"")))
  }

  def testParensFunInheritReturnTypeParens(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "def foo() = \"\"")))
  }

  def testVarInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "var foo = \"\"")))
  }

  def testVarInheritReturnTypeParens(): Unit = {
    //TODO: this, for some reason, does not work in compiler
    assertMatches(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "var foo = \"\""))) {
      case Error(_, "Overriding type String does not conform to base type () => Int") :: Nil =>
    }
  }

  def testSCL14152(): Unit = {
    val code =
      """
        |sealed trait TagExpr
        |
        |sealed trait Composite extends TagExpr {
        |  def head: TagExpr
        |  def tail: Seq[TagExpr]
        |  def all: Seq[TagExpr] = head +: tail
        |}
        |
        |final case class And(head: TagExpr, tail: TagExpr*) extends Composite
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL14922(): Unit = {
    val code =
      """
        |trait A {
        |  trait Internal
        |  val i = new Internal {}
        |}
        |trait B extends A {
        |  trait Internal extends super.Internal
        |  override val i = new Internal {}
        |}
        |trait C extends A {
        |  trait Internal extends super.Internal
        |  override val i = new Internal {}
        |}
        |trait D extends B with C {
        |  trait Internal extends super[B].Internal with super[C].Internal
        |  override val i = new Internal {}
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL14707(): Unit = {
    val code =
      """
        |trait BaseComponent {
        |  trait BaseComponent {
        |    val abstractName: String
        |  }
        |}
        |
        |trait AbstractChildComponent extends BaseComponent {
        |  trait AbstractChildComponent extends AbstractChildComponent.super[BaseComponent].BaseComponent {
        |    def abstractMethod() : scala.Unit
        |  }
        |}
        |
        |trait ConcreteComponent extends AbstractChildComponent {
        |
        |  object ConcreteComponent extends AbstractChildComponent {
        |    override def abstractMethod(): Unit = ()
        |
        |    override val abstractName = "hello world"
        |  }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }
}
