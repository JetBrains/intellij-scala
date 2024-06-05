package org.jetbrains.plugins.scala.annotator
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ApplyTest extends AnnotatorLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  def testSCL10902(): Unit = {
    checkTextHasNoErrors(
      """
        |object Test extends App {
        | class A { def apply[Z] = 42 }
        | def create = new A
        |
        | create[String]
        |}
      """.stripMargin)
  }

  def testSCL13689(): Unit = {
    checkTextHasNoErrors(
      """
        |class parent {
        |  def abc[T]: T = ???
        |}
        |
        |object foo extends parent {
        |  def abc: Nothing = ???
        |}
        |
        |object bar {
        |  foo.abc[Int]
        |}
      """.stripMargin)
  }

  def testSCL10253(): Unit = {
    val code =
      """
        |import PersonObject.Person
        |package object PersonObject {
        |
        |  case class Person(name: String, age: Int)
        |
        |  object Person {
        |    def apply() = new Person("<no name>", 0)
        |  }
        |
        |}
        |
        |class CaseClassTest {
        |  val b = Person("William Shatner", 82)
        |}""".stripMargin

    checkTextHasNoErrors(code)
  }

  def testSCL11344(): Unit = {
    checkTextHasNoErrors(
      """
        |import ObjectInt.{CaseClassInObjectInt}
        |
        |
        |trait CommonObjectTraitWithApply[T] {
        |  def apply(arg: T): T = arg
        |
        |}
        |
        |object ObjectInt extends CommonObjectTraitWithApply[Int]{
        |  ObjectInt(123)
        |  case class CaseClassInObjectInt()
        |}
        |
    """.stripMargin)
  }

  def testApplyFromImplicitConversion(): Unit = {
    val code =
      """
        |object Holder {
        |  class A
        |  class B
        |
        |  def f: A = ???
        |
        |  class AExt {
        |    def apply(b: B): B = ???
        |  }
        |  implicit def aExt: A => AExt = ???
        |
        |  f(new B)
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testExampleFromScalaz(): Unit = {
    val code =
      """
        |object Holder {
        |  import scala.language.{higherKinds, reflectiveCalls}
        |  import scala.concurrent.Future
        |
        |  trait OptionT[F[_], A]
        |
        |  trait NaturalTransformation[-F[_], +G[_]] {
        |    def apply[A](fa: F[A]): G[A]
        |  }
        |
        |  type ~>[-F[_], +G[_]] = NaturalTransformation[F, G]
        |
        |  def optionT[M[_]] = new (({type λ[α] = M[Option[α]]})#λ ~> ({type λ[α] = OptionT[M, α]})#λ) {
        |    def apply[A](a: M[Option[A]]): OptionT[M, A] = ???
        |  }
        |
        |  trait OptionTFunctions {
        |    def optionT[M[_]] = new (({type λ[α] = M[Option[α]]})#λ ~> ({type λ[α] = OptionT[M, α]})#λ) {
        |      def apply[A](a: M[Option[A]]) = ???
        |    }
        |  }
        |
        |  val futureOption: Future[Option[String]] = ???
        |
        |  optionT(futureOption)
        |}
      """.stripMargin
    checkTextHasNoErrors(code)
  }

  def testScl11112(): Unit = {
    checkTextHasNoErrors(
      """
        |  object Table {
        |    def apply[A](heading: String, rows: A*) = ???
        |    def apply[A, B](heading: (String, String), rows: (A, B)*) = ???
        |  }
        |
        |  object TableUser {
        |    Table(("One", "Two"), ("A", "B"))
        |  }
      """.stripMargin)
  }

  def testOverloadedApplyNamedParametersWithImplicitNeeded(): Unit = {
    checkTextHasNoErrors(
      """
        |class Type
        |object Type {
        |  implicit def bool2Type(bool: Boolean): Type = new Type
        |}
        |
        |class ToTargetType
        |object ToTargetType {
        |  implicit def toType(toTargetType: ToTargetType): TargetType = new TargetType
        |}
        |class TargetType
        |
        |object Test {
        |  def apply(a: Int = 1, b: String = "2", c: Boolean = false): ToTargetType = new ToTargetType
        |  def apply(two: Type*): TargetType = new TargetType
        |}
        |
        |object bar {
        |  val wasBroken: TargetType = Test.apply(c = true)
        |  val tt: TargetType = Test.apply(true, false, true)
        |}
      """.stripMargin)
  }

  def testSCL21542(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  val x: UInt = ???
      |  val counter = Reg(x)
      |}
      |
      |trait SpinalEnum { type C }
      |trait UInt extends Data
      |trait Data
      |trait HardType[T]
      |
      |object HardType {
      |  implicit def implFactory[T <: Data](t: T): HardType[T] = ???
      |}
      |
      |object Reg {
      |  def apply[T <: Data](dataType: HardType[T]): T = ???
      |  def apply[T <: SpinalEnum](enumType: T): enumType.C = ???
      |}
      |""".stripMargin
  )

  def testSCL21742(): Unit = checkTextHasNoErrors(
    """
      |case class Bool() {
      |  def unary_! : Bool = ???
      |  def &&&(other: Bool) : Bool = ???
      |}
      |
      |class ElseWhenClause() {}
      |
      |case class Reproduction() {
      |  implicit class ElseWhenClauseBuilder(cond: Bool) {
      |    def apply(block: => Unit): ElseWhenClause = ???
      |  }
      |
      |  val x = Bool()
      |  val e1: ElseWhenClause = (!x) {} // ERROR: unary_! does not take parameters
      |  val e2: ElseWhenClause = (!x &&& !x) { } //OK
      |}
      |""".stripMargin
  )

  def testSCL21616(): Unit = checkTextHasNoErrors(
    """
      |class MyClass {
      |  def apply(booleanParameter: Boolean): Int = ???
      |}
      |
      |object wrapper {
      |  val myMethod: MyClass = ???
      |  myMethod(true)
      |  myMethod.apply(true)
      |  //OK: named argument is ok for `apply` method
      |  myMethod(booleanParameter = true)
      |
      |  object inner1 {
      |    extension (target: MyClass)
      |      def apply(doubleParameter: Double): Int = ???
      |
      |    myMethod(42d)
      |    myMethod.apply(doubleParameter = 42d)
      |    //BAD: named argument is not recognised for `apply` extension method
      |    myMethod(doubleParameter = 42d)
      |  }
      |
      |  object inner2 {
      |    //scala 2 style extensions
      |    implicit class MyClassOps42(private val target: MyClass) extends AnyVal {
      |      final def apply(stringParameter: String): Int = ???
      |    }
      |
      |    myMethod("42")
      |    myMethod.apply(stringParameter = "42")
      |    //BAD: named argument is not recognised for `apply` extension method (Scala 2 style)
      |    myMethod(stringParameter = "42")
      |  }
      |}
      |""".stripMargin
  )

  def testSCL8967(): Unit = checkTextHasNoErrors(
    """
      |object ATest extends App {
      |    implicit class OptApply(val value: Option[_]) extends AnyVal {
      |        def apply[T](code: T => Unit): Unit = {}
      |    }
      |    val sxOp = Some(new SX)
      |    sxOp.apply[SX] { sx => sx.test() }
      |    sxOp[SX] { sx => sx.test() } //!!! "sx" is red, "test()" is red
      |}
      |class SX {def test(): Unit = {}}
      |""".stripMargin
  )

  def testSCL22917(): Unit = checkTextHasNoErrors(
    """
      |def m[A] = new C[A]
      |class C[A] {
      |  def apply[B, C](f: A => (B, C)) = f
      |}
      |def r = m[Int](_.toString -> false)
      |""".stripMargin
  )
}
