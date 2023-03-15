package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class PatternTypeInferenceTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3_0

  def testSCL13746(): Unit = checkTextHasNoErrors(
    """
      |import scala.annotation.tailrec
      |
      |
      |trait IO[A] {
      |  def flatMap[B](f: A => IO[B]): IO[B] = FlatMap(this, f)
      |
      |  def map[B](f: A => B): IO[B] = flatMap(f andThen (Return(_)))
      |}
      |
      |case class Return[A](a: A) extends IO[A]
      |
      |case class Suspend[A](r: () => A) extends IO[A]
      |
      |case class FlatMap[A, B](s: IO[A], k: A => IO[B]) extends IO[B]
      |
      |object IO {
      |  @tailrec
      |  def run[A](io: IO[A]): A = io match {
      |    case Return(a) => a
      |    case Suspend(r) => r()
      |    case FlatMap(x, f) => x match {
      |      case Return(a) => run(f(a))
      |      case Suspend(r) => run(f(r()))
      |      case FlatMap(y, g) => run(y flatMap (a => g(a) flatMap f))
      |    }
      |  }
      |}""".stripMargin
  )

  def testSCL15366(): Unit = checkTextHasNoErrors(
    """
      |sealed abstract class A
      |
      |case class B[X](x: X) extends A
      |
      |case class C[X](x: X) extends A
      |
      |def swap(a: A): A = a match {
      |  case b: B[x] =>
      |    C[x](b.x)
      |  case c: C[x] =>
      |    B[x](c.x)
      |}
      |
      |val a = swap(B(42))
      |val b = swap(C("7"))""".stripMargin
  )

  //@TODO:
//  def testInstantiateScrutineeTypeVariables(): Unit = checkTextHasNoErrors(
//    """
//      |trait Base[A, B]
//      |trait ChildA[A] extends Base[A, B]
//      |
//      |def foo[A, B](child: Base[A, B], doesntWork: A): Boolean =
//      |  child match {
//      |    case childA: ChildA[s] =>
//      |      val a: A = ???
//      |      val b: B = ???
//      |      implicitly[s =:= A]
//      |      implicitly[A =:= B]
//      |      false
//      |  }
//      |""".stripMargin
//  )

//  def testSCL14197(): Unit = checkTextHasNoErrors(
//    """
//      |class Base[A, B]
//      |case class ChildA[T]() extends Base[T, T]
//      |case class ChildB[T]() extends Base[AnyRef with T, T]
//      |
//      |object BugExample extends App {
//      |  def foo[A, B](child: Base[A, B], doesntWork: A): B = child match {
//      |    case childA: ChildA[_] => barA(childA, doesntWork) // problem with B
//      |    case childB: ChildB[_] => barB(childB, doesntWork) // problem with A
//      |  }
//      |
//      |  def barA[T](child: ChildA[T], a: T): T = a
//      |  def barB[T](child: ChildB[T], a: AnyRef with T): T = a
//      |
//      |  println(foo(ChildA[Int](), 1))
//      |  println(foo(ChildB[String](), "2"))
//      |}""".stripMargin
//  )

  def testSCL16108(): Unit = checkTextHasNoErrors(
    """
      |sealed trait Maybe[+A] {
      |  def interpret[Z](ifEmpty: => Z, f: A => Z): Z =
      |    this match {
      |      case Absent => ifEmpty
      |      case Present(value) => f(value)
      |      case Map(opt, f0) => opt.interpret(ifEmpty, f.compose(f0))
      |      case Chain(opt, f0) => opt.interpret(ifEmpty, a0 => f0(a0).interpret(ifEmpty, f))
      |    }
      |}
      |case class Present[A](value: A) extends Maybe[A]
      |case object Absent extends Maybe[Nothing]
      |case class Map[A0,A](opt: Maybe[A0], f0: A0 => A) extends Maybe[A]
      |case class Chain[A0,A](opt: Maybe[A0], f0: A0 => Maybe[A]) extends Maybe[A]
      |""".stripMargin
  )

  def testSCL16314(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  case class CC[A]()
      |  def fn[A](cc: CC[A]): List[A] = cc match {
      |    case _: CC[a] => List[a]()
      |  }
      |}""".stripMargin
  )

  def testSCL16564(): Unit = checkTextHasNoErrors(
    """
      |import scala.io.Source
      |object Test {
      |  def fromInput[A](in: Input[A]): Stream[A] = in match {
      |    case InputFile(path) => Stream.empty[Byte]
      |    case TextInput(path) => Source.fromFile(path).getLines().toStream
      |  }
      |}
      |sealed trait Input[A]
      |case class InputFile(path: String) extends Input[Byte]
      |case class TextInput(path: String) extends Input[String]
      |""".stripMargin
  )

  def testSCL17424(): Unit = checkTextHasNoErrors(
    """
      |object permissions {
      |  final case class Item(title: String)
      |  sealed trait Permission[A]
      |  case object Create extends Permission[Unit]
      |  case object Edit extends Permission[Item]
      |  def verify[A](permission: Permission[A], value: A): Boolean = permission match {
      |    case Create => false
      |    case Edit => value.title == "editable" // Cannot resolve symbol title
      |  }
      |}
      |""".stripMargin
  )

  def testSCL17512(): Unit = checkTextHasNoErrors(
    """
      |object Main {
      |  trait TypedKey[A]
      |  final class TypedMap() {
      |    def get[A](key: TypedKey[A]): Option[A] = None
      |  }
      |  val keys: Seq[TypedKey[_]] = Seq()
      |  val map: TypedMap = new TypedMap()
      |  // Note the use of `TypedKey[t]` to bind the
      |  // type parameter for a single key
      |  keys.map { case key: TypedKey[t] =>
      |    // Incorrect red underline on map.get(key)
      |    val value: Option[t] = map.get(key)
      |    System.out.println(value)
      |  }
      |}""".stripMargin
  )

  def testSCL15405(): Unit = checkTextHasNoErrors(
    """
      |class SomeBase {
      |  type A
      |}
      |
      |trait SomeType[C] extends SomeBase {
      |  def getA: A
      |}
      |
      |trait Test {
      |  def x(i: SomeBase#A)
      |
      |  def provideExecutionEnvironment[C <: SomeBase](construct: C): Unit = {
      |    construct match {
      |      case c: SomeType[_] =>
      |        x(c.getA)
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def testIntersect(): Unit = checkTextHasNoErrors(
    """
      |trait Foo[A]; trait Foo2[A]; trait Foo3
      |trait Bar extends Foo[Int] with Foo3
      |trait Baz[T] extends Foo[T] with Foo2[String] { def foo: T = ??? }
      |
      |(??? : Bar) match {
      |  case tt: Baz[t] =>
      |    val x: Int = tt.foo
      |    val t: t = 123
      |}
      |""".stripMargin
  )

  def testSCL21119(): Unit = checkTextHasNoErrors(
    """
      |object IncorrectTypeMismatch {
      |  sealed trait Foo[T]
      |
      |  trait Bar[A]
      |
      |  case object FooString extends Foo[String]
      |
      |  val string: Bar[String] = new Bar[String] {}
      |
      |  def findBar[P](tag: Foo[P]): Option[Bar[P]] =
      |    tag match {
      |      case FooString => Some(string) // shows type mismatch
      |      case _ => None
      |    }
      |
      |  def foo(): Unit = {
      |    val tag = new Foo[String] {}
      |    val bar = findBar(tag)
      |  }
      |}""".stripMargin
  )

  //@TODO: in scala 3 type parameters of enclosing enums (only?) are also instantiated
//  def testEnum(): Unit = checkTextHasNoErrors(
//    """
//      |enum Func[-A, +B] {
//      |  case Double extends Func[Int, Int]
//      |  case ToString extends Func[Float, String]
//      |
//      |  def run: A => B = this match {
//      |    case Double => (x: Int) => x * 2
//      |    case ToString => (x: Float) => x.toString
//      |  }
//      |}
//      |""".stripMargin
//  )
//
}
