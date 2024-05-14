package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3ExtensionsTest extends ScalaLightCodeInsightFixtureTestCase {
  override def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSimpleExtension(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  case class Circle(x: Double, y: Double, radius: Double)
      |
      |  extension (c: Circle)
      |    def circumference: Double = c.radius * math.Pi * 2
      |
      |  val c: Circle = ???
      |  c.circumference
      |}
      |""".stripMargin
  )

  def testSimpleDesugaredInvocation(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  case class Circle(x: Double, y: Double, radius: Double)
      |
      |  extension (c: Circle)
      |    def circumference: Double = c.radius * math.Pi * 2
      |
      |  val c: Circle = ???
      |  circumference(c)
      |}
      |""".stripMargin
  )

  //@TODO: right-associative?
  def testOperators(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  extension (x: String)
      |    def < (y: String): Boolean = true
      |
      |  "123" < "4235"
      |}
      |""".stripMargin
  )

  def testGenericExtension(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  extension [T](xs: List[T])
      |    def second: T = ???
      |
      |  val xs: List[Int] = ???
      |  val x: Int = xs.second
      |}
      |""".stripMargin
  )

  def testCollectiveExtension(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  extension (ss: Seq[String])
      |    def longestStrings: Seq[String] = ???
      |    def longestString: String = ???
      |
      |  val xs: Seq[String] = ???
      |  val longest: Seq[String] = xs.longestStrings
      |  val singleLongest: String = xs.longestString
      |}
      |""".stripMargin

  )

  def testTwoTypeArgumentSectionsOnInvocation(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  extension [A](x: Int) { def method[B](y: Int) = () }
      |  method[Int](1)[Long](2)
      |}
      |""".stripMargin
  )

  def testPriorityOfVisibleExtensionOverVisibleConversion(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  extension (x: Int) { def foo: Int = 123 }
      |  implicit class IntOps(val x: Int) { def foo: Int = 123 }
      |
      |  123.foo
      |}
      |""".stripMargin
  )

  def testExtensionFromGivenInLexicalScope(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait F
      |  given F with {
      |    extension (x: Int) { def foo: Int = 123 }
      |  }
      |
      |  123.foo
      |}
      |""".stripMargin
  )

  def testExtensionFromImplicitScope(): Unit = checkTextHasNoErrors(
    """
      |trait List[T]
      |object List {
      |  extension [T, U](xs: List[T])(using t: Ordering[U])
      |    def foo(t: U): Int = ???
      |}
      |
      |object A {
      |  given Ordering[String] = ???
      |  val xs: List[Int] = ???
      |  val y: Int = xs.foo("123")
      |}
      |""".stripMargin
  )

  def testExtensionFromGivenInImplicitScope(): Unit = checkTextHasNoErrors(
    """
      |trait List[T]
      |object List {
      |  given Ordering[List[Int]] with {
      |    def compare(xs: List[Int], ys: List[Int]): Int = 1
      |
      |    extension [T, U](xs: List[T])(using t: Ordering[U])
      |      def foo(t: U): U = ???
      |  }
      |}
      |
      |object A {
      |  trait F
      |  given Ordering[F] = ???
      |  val xs: List[Int] = ???
      |  val f: F = ???
      |  val y: F = xs.foo(f)
      |}
      |""".stripMargin
  )

  // SCL-21319
  def testImportedExtensionFromGiven(): Unit = checkTextHasNoErrors(
    """
      |object GivensWithExtensions:
      |  given anyref: AnyRef with
      |    extension (s: String)
      |      def upper: String = s.toUpperCase
      |
      |object Import:
      |  def foo: Unit =
      |    import GivensWithExtensions.given
      |    println("abcde".upper)
      |  def bar: Unit =
      |    import GivensWithExtensions.anyref
      |    println("abcde".upper)
      |""".stripMargin
  )

  //actually compiles, but probably should not
  //upd: 29.05.2023, since cases like this are in scala 3 stdlib,
  //I had to introduce a workaround to maintain bug-to-bug compatibility
  //with compiler.
  def testAmbiguousExtensionAndConversion(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait F
       |  given F with {
       |    extension (x: Int) { def foo: Int = 123 }
       |  }
       |
       |  implicit class IntOps(val x: Int) { def foo: Int = 123 }
       |  123.fo${CARET}o
       |}
       |""".stripMargin
  )

  def testAmbiguousExtensionAndConversion2(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       |  trait F
       |  given F with {
       |    extension (x: Int) { def foo: Int = 123 }
       |  }
       |
       |  class IntOps(val x: Int) { def foo: Int = 123 }
       |
       |  given Conversion[Int, IntOps] = new IntOps(_)
       |
       |  123.fo${CARET}o
       |}
       |""".stripMargin
  )

  //see comment above.
  def testAmbiguousExtensionAndConversionImplicitScope(): Unit = checkTextHasNoErrors(
    s"""
       |trait List[T]
       |object List {
       |  extension [T](xs: List[T])
       |    def foo(u: String): Int = ???
       |
       |  implicit class ListOps[T](xs: List[T]) {
       |    def foo(t: String): Int = 123
       |  }
       |}
       |
       |object A {
       |  val xs: List[Int] = ???
       |  xs.fo${CARET}o("123")
       |}
       |""".stripMargin
  )

  def testAmbiguousExtensionsWithExpectedType(): Unit = {
    checkTextHasNoErrors(
      """
        |object B:
        |  trait F
        |  given F with {
        |    extension (x: Int) { def foo: Int = 123 }
        |  }
        |
        |  trait G
        |  given G with {
        |    extension (x: Int) { def foo: String = "123" }
        |  }
        |
        |  val s: Int = 123.foo
        |""".stripMargin
    )
  }

  //@TODO: Currently does not compile, but probably should,
  //       nobody in epfl seems to know why.
  def testAmbiguousExtensionWithExpectedTypeAndTypeArgs(): Unit = checkTextHasNoErrors(
    s"""
      |object B {
      |  trait F
      |  given F with {
      |    extension (x: Int) { def foo[X]: X = ??? }
      |  }
      |
      |  trait G
      |  given G with {
      |    extension (x: Int) { def foo[Y]: String = "123" }
      |  }
      |
      |  val s: Int = 123.foo[Int]
      |}""".stripMargin
  )

  //@TODO: Currently does not compile, but probably should,
  //       nobody in epfl seems to know why.
  def testAmbiguousExtensionWithExpectedTypeAndArgs(): Unit = checkTextHasNoErrors(
    s"""
      |object B {
      |  trait F
      |  given F with {
      |    extension (x: Int) { def foo(i: Int): Int = ??? }
      |  }
      |
      |  trait G
      |  given G with {
      |    extension (x: Int) { def foo(i: Int): String = "123" }
      |  }
      |
      |  val s: Int = 123.foo(1)
      |}""".stripMargin
  )


  def testResolveFromInsideExtension(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  extension (s: String)
      |    def position(ch: Char, n: Int): Int =
      |      if n < s.length && s(n) != ch then position(ch, n + 1)
      |      else n
      |
      |  extension [T](x: T)
      |    def f: Int = g
      |    def g: Int = 123
      |}
      |""".stripMargin
  )

  def testExtensionFromContextBound(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Functor[F[_]] {
      |    extension [A, B](fa: F[A]) def map(f: A => B): F[B]
      |  }
      |
      |  def foo[F[_]: Functor](fi: F[Int], toS: Int => String): F[String] = fi.map(toS)
      |}
      |""".stripMargin
  )

  def testExtensionFromTypeClassInstance(): Unit = checkTextHasNoErrors(
    """
      |trait Ord[A] {
      |  extension (xs: A) def foo: Int = 123
      |}
      |
      |trait List[T]
      |object List {
      |  implicit def ordList(implicit ord: Ord[Int]): Ord[List[Int]] = new Ord[List[Int]] {}
      |}
      |
      |object A {
      |  implicit val ordInt: Ord[Int] = new Ord[Int] {}
      |
      |  val xs: List[Int] = new List[Int] {}
      |  println(xs.foo)
      |}
      |""".stripMargin
  )

  def testExtensionFromTypeClassInstanceNeg(): Unit = checkHasErrorAroundCaret(
    s"""
      |trait Ord[A] {
      |  extension (xs: A) def foo: Int = 123
      |}
      |
      |trait List[T]
      |object List {
      |  implicit def ordList(implicit ord: Ord[Int]): Ord[List[Int]] = new Ord[List[Int]] {}
      |}
      |
      |object A {
      |  val xs: List[Int] = new List[Int] {}
      |  println(xs.f${CARET}oo)
      |}
      |""".stripMargin
  )

  def testExtensionFromGiven(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  trait Monad[F[_]] {
      |    extension[A,B](fa: F[A])
      |      def flatMap(f: A => F[B]):F[B]
      |  }
      |
      |  given optionMonad: Monad[Option] with
      |    def pure[A](a: A) = Some(a)
      |    extension[A,B](fa: Option[A])
      |      def flatMap(f: A => Option[B]) = {
      |        fa match {
      |          case Some(a) =>
      |            f(a)
      |          case None =>
      |            None
      |        }
      |      }
      |
      |  Option(123).flatMap(x => Option(x + 1))
      |}
      |""".stripMargin
  )

  def testExtensionRenamed(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  object Extensions:
      |    extension (s: String)
      |      def foo: Int = s.length
      |      def bar: Char = s.head
      |
      |  object Test extends App:
      |    import Extensions.{foo => baz, *}
      |    println("...".baz)
      |    println("...".bar)
      |}
      |""".stripMargin
  )

  def testSCL20701(): Unit = checkTextHasNoErrors(
    """
      |extension (s: String) {
      |  def foo(x: Int) = "Int"
      |  def foo(x: String) = "Int"
      |}
      |
      |val s = "abc"
      |s.foo(3) // can't find declaration to go
      |s.foo("abc") // can't find declaration to go
      |""".stripMargin
  )

  def testSCL19820(): Unit = checkTextHasNoErrors(
    """
      |case class MyClass(i: Int)
      |
      |object MyClassExtensions {
      |  extension (m: MyClass) {
      |    def myDef(x: Int): Int = m.i + x
      |    def myDef(x: Double): Double = m.i + x
      |    def apply(x: Int): Int = m.i + x
      |  }
      |}
      |
      |object testMain {
      |  def main(args: Array[String]): Unit = {
      |    import MyClassExtensions._
      |    val myClass = MyClass(1)
      |    val output1 = myClass.myDef(1)
      |    val output2 = myClass.myDef(1.0)
      |    val output3 = myClass(1)
      |    val output4 = myClass.apply(1)
      |
      |    println(output1.isInstanceOf[Int])    // Prints true
      |    println(output2.isInstanceOf[Double]) // Prints true
      |    println(output3.isInstanceOf[Int])    // Prints true
      |    println(output4.isInstanceOf[Int])    // Prints true
      |  }
      |}
      |""".stripMargin
  )

  def testSCL21095(): Unit = checkTextHasNoErrors(
    """
      |object ExtensionOverride {
      |  class Foo
      |
      |  extension (f: Foo) {
      |    def apply(x: String): String = "str"
      |  }
      |
      |  def main(args: Array[String]): Unit = {
      |    val instance = new Foo
      |    val bad: String = instance("a")
      |  }
      |}
      |""".stripMargin
  )

  def testSCL21053(): Unit = checkTextHasNoErrors(
    """
      |object TestIntellij:
      |
      |  extension [X](x: X)
      |    def foo[Y](using Bar[X, Y]): Map[X, Y] = ???
      |
      |  trait Bar[X, Y]
      |  given Bar[Int, Int]    = ???
      |  given Bar[Int, String] = ???
      |  given Bar[Int, Float]  = ???
      |
      |  def main(args: Array[String]) =
      |    val intint    = 1.foo[Int] // Map[Int, Int], but intellij says it's an Any
      |    val intFloat  = 1.foo[Float] // Map[Int, Float], but intellij says it's an Any
      |    val intString = 1.foo[String] // Map[Int, String], but intellij says it's an Any
      |""".stripMargin
  )

  def testSCL21060(): Unit = checkTextHasNoErrors(
    """
      |object Opaque {
      |  object Scope:
      |    opaque type MyOpaqueType = String
      |
      |    object MyOpaqueType: //TODO completion of companion object name doesn't work
      |      extension (t: MyOpaqueType)
      |        def myExtensionForOpaque: String = "42"
      |
      |  def main(): Unit = {
      |    val valueOpaque: Scope.MyOpaqueType = ???
      |    valueOpaque.myExtensionForOpaque
      |  }
      |}
      |""".stripMargin
  )

  def testSCL21520(): Unit = checkTextHasNoErrors(
    """
      |object Abstract {
      |  object Scope:
      |    type MyAbstractType
      |
      |    object MyAbstractType:
      |      extension (t: MyAbstractType)
      |        def myExtensionForAbstract: String = "42"
      |
      |  def main(): Unit = {
      |    val valueOpaque: Scope.MyAbstractType = ???
      |    valueOpaque.myExtensionForAbstract
      |  }
      |}
      |""".stripMargin
  )

  def testSCL21084(): Unit = checkTextHasNoErrors(
    """
      |
      |extension [T <: Tuple](t: T) {
      |  def id = t
      |}
      |
      |implicit class TupleExts[T <: Tuple](private val t: T) extends AnyVal {
      |  def id2 = t
      |}
      |
      |val ti = (Option(1), Option(2), Option("3")).id
      |val ti2 = (Option(1), Option(2), Option("3")).id2
      |""".stripMargin
  )

  def testSCL21257(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  val iarr = IArray(1, 2, 3)
      |  iarr.length
      |  iarr.map(_ + 1)
      |  iarr(123)
      |}
      |""".stripMargin
  )

  def testSCL21416(): Unit = checkTextHasNoErrors(
    """
      |trait Functor[F[_]]:
      |  extension [A, B](fa: F[A])
      |    def ffmap(f: A => B): F[B]
      |
      |given eitherFunctor[E]: Functor[[A] =>> Either[E, A]] with
      |  extension[A, B] (x: Either[E, A])
      |    def ffmap(f: A => B): Either[E, B] = x match
      |      case Left(err) => Left(err)
      |      case Right(a) => Right(f(a))
      |
      |object A {
      |  val e1: Either[String, Int] = Right(10)
      |  val e3 = e1.ffmap(a => a + 1)
      |}
      |""".stripMargin
  )

  def testSCL21416_Constrained(): Unit = {
    checkHasErrorAroundCaret(
      s"""
        |trait Functor[F[_]]:
        |  extension [A, B](fa: F[A])
        |    def ffmap(f: A => B): F[B]
        |
        |
        |trait F[A]
        |given fInt: F[Int] = ???
        |given eitherFunctor[E: F]: Functor[[A] =>> Either[E, A]] with
        |  extension[A, B] (x: Either[E, A])
        |    def ffmap(f: A => B): Either[E, B] = x match
        |      case Left(err) => Left(err)
        |      case Right(a) => Right(f(a))
        |
        |object A {
        |  val e1: Either[String, Int] = Right(10)
        |  val e3 = e1.ffm${CARET}ap(a => a + 1)
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """
        |trait Functor[F[_]]:
        |  extension [A, B](fa: F[A])
        |    def ffmap(f: A => B): F[B]
        |
        |trait F[A]
        |given fInt: F[Int] = ???
        |given eitherFunctor[E: F]: Functor[[A] =>> Either[E, A]] with
        |  extension[A, B] (x: Either[E, A])
        |    def ffmap(f: A => B): Either[E, B] = x match
        |      case Left(err) => Left(err)
        |      case Right(a) => Right(f(a))
        |
        |object A {
        |  val e1: Either[Int, Int] = Right(10)
        |  val e3 = e1.ffmap(a => a + 1)
        |}
        |""".stripMargin
    )
  }

  def testSCL21637(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  val v = "".test()
      |
      |  extension (string: String)
      |    private def test() = string
      |}
      |""".stripMargin
  )

  def testSCL22495(): Unit = checkTextHasNoErrors(
    """
      |object Example:
      |  val outer: Option[Int] = null
      |  outer.maximum
      |
      |  extension (t: Option[Int])
      |    def maximum: Int =
      |      val inner: Option[Int] = null
      |      inner.maximum
      |""".stripMargin
  )

  def testSCL21732(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  object Test {
      |    extension (ls: List[String & Int])
      |      private def test[A <: Double]: List[String & A] = ???
      |
      |    def infer: Seq[String & Double] = List.empty[String & Int].test[Double]
      |  }
      |}
      |""".stripMargin
  )
}
