package org.jetbrains.plugins.scala
package lang.typeInference

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3ExtensionsTest extends ScalaLightCodeInsightFixtureTestCase {
  override def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_7

  override def checkTextHasNoErrors(@Language("Scala 3") text: String): Unit =
    super.checkTextHasNoErrors(text)

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

  def testPriorityOfVisibleExtensionOverVisibleConversionNewStyle(): Unit = checkTextHasNoErrors(
    """
      |object A {
      |  extension (x: Int) { def foo: Int = 123 }
      |  class IntOps(val x: Int) { def foo: Int = 123 }
      |  given c: Conversion[Int, IntOps] = ???
      |  123.foo
      |}
      |""".stripMargin
  )

  // SCL-25859
  def testDirectExtensionReceiverHasPriorityOverConversionForSameExtensionName(): Unit = checkTextHasNoErrors(
    """
      |class DirectReceiver
      |class ConvertedReceiver
      |
      |given Conversion[DirectReceiver, ConvertedReceiver] = null
      |
      |extension (receiver: ConvertedReceiver) def choose(argument: String): Unit = ()
      |extension (receiver: DirectReceiver) def choose(argument: String): Unit = ()
      |
      |(null: DirectReceiver).choose("")
      |""".stripMargin
  )

  def testDirectExtensionReceiverHasPriorityForSymbolicNameAndOldStyleConversion(): Unit = checkTextHasNoErrors(
    """
      |class DirectReceiver
      |class ConvertedReceiver
      |
      |implicit def convert(receiver: DirectReceiver): ConvertedReceiver = null
      |
      |extension (receiver: ConvertedReceiver) def /(argument: String): Unit = ()
      |extension (receiver: DirectReceiver) def /(argument: String): Unit = ()
      |
      |(null: DirectReceiver) / ""
      |""".stripMargin
  )

  def testDirectGenericExtensionReceiverHasPriorityOverConversionForSameExtensionName(): Unit = checkTextHasNoErrors(
    """
      |class DirectReceiver
      |class ConvertedReceiver
      |
      |given Conversion[DirectReceiver, ConvertedReceiver] = null
      |
      |extension (receiver: ConvertedReceiver) def choose(argument: String): Unit = ()
      |extension [T](receiver: T) def choose(argument: String): Unit = ()
      |
      |(null: DirectReceiver).choose("")
      |""".stripMargin
  )

  def testConversionStillMakesExtensionReceiverApplicableWhenNoDirectExtensionExists(): Unit = checkTextHasNoErrors(
    """
      |class OriginalReceiver
      |class ConvertedReceiver
      |
      |given Conversion[OriginalReceiver, ConvertedReceiver] = null
      |
      |extension (receiver: ConvertedReceiver) def choose(argument: String): Unit = ()
      |
      |(null: OriginalReceiver).choose("")
      |""".stripMargin
  )

  def testDirectSubtypeExtensionReceiverIsMoreSpecificThanDirectSupertypeReceiver(): Unit = checkTextHasNoErrors(
    """
      |class ParentReceiver
      |class ChildReceiver extends ParentReceiver
      |
      |extension (receiver: ParentReceiver) def choose(argument: String): Unit = ()
      |extension (receiver: ChildReceiver) def choose(argument: String): Unit = ()
      |
      |(null: ChildReceiver).choose("")
      |""".stripMargin
  )

  def testTwoConversionAssistedExtensionReceiversRemainAmbiguous(): Unit = checkHasErrorAroundCaret(
    s"""
       |class OriginalReceiver
       |class FirstConvertedReceiver
       |class SecondConvertedReceiver
       |
       |given Conversion[OriginalReceiver, FirstConvertedReceiver] = null
       |given Conversion[OriginalReceiver, SecondConvertedReceiver] = null
       |
       |extension (receiver: FirstConvertedReceiver) def choose(argument: String): Unit = ()
       |extension (receiver: SecondConvertedReceiver) def choose(argument: String): Unit = ()
       |
       |(null: OriginalReceiver).cho${CARET}ose("")
       |""".stripMargin
  )

  def testWrongLaterArgumentOnDirectReceiverDoesNotFallBackToConvertedReceiver(): Unit = checkHasErrorAroundCaret(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: DirectReceiver) def choose(argument: Int): Unit = ()
       |extension (receiver: ConvertedReceiver) def choose(argument: String): Unit = ()
       |
       |(null: DirectReceiver).choose("${CARET}")
       |""".stripMargin
  )

  def testExpectedResultTypeDoesNotSelectConvertedReceiverOverDirectReceiver(): Unit = checkHasErrorAroundCaret(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |class DirectResult
       |class ConvertedResult
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: DirectReceiver) def choose: DirectResult = null
       |extension (receiver: ConvertedReceiver) def choose: ConvertedResult = null
       |
       |val result: ConvertedResult = (null: DirectReceiver).cho${CARET}ose
       |""".stripMargin
  )

  def testLaterArgumentConversionDoesNotLowerPriorityOfDirectReceiverExtension(): Unit = checkTextHasNoErrors(
    """
      |class DirectReceiver
      |class ConvertedReceiver
      |class OriginalArgument
      |class ConvertedArgument
      |
      |given Conversion[DirectReceiver, ConvertedReceiver] = null
      |given Conversion[OriginalArgument, ConvertedArgument] = null
      |
      |extension (receiver: DirectReceiver) def choose(argument: ConvertedArgument): Unit = ()
      |extension (receiver: ConvertedReceiver) def choose(argument: OriginalArgument): Unit = ()
      |
      |(null: DirectReceiver).choose(null: OriginalArgument)
      |""".stripMargin
  )

  def testExtensionReceiverConversionsAreNotChained(): Unit = checkHasErrorAroundCaret(
    s"""
       |class OriginalReceiver
       |class IntermediateReceiver
       |class FinalReceiver
       |
       |given Conversion[OriginalReceiver, IntermediateReceiver] = null
       |given Conversion[IntermediateReceiver, FinalReceiver] = null
       |
       |extension (receiver: FinalReceiver) def choose: Unit = ()
       |
       |(null: OriginalReceiver).cho${CARET}ose
       |""".stripMargin
  )

  def testPriorityOfExtensionOverConversionFromImplicitScope(): Unit = checkTextHasNoErrors(
    """
      |trait A
      |object A {
      |  extension (a: A) { def foo: Int = 123 }
      |  class AOps { def foo: Int = 123 }
      |  given c: Conversion[A, AOps] = ???
      |}
      |object Test {
      |  val a: A = ???
      |  a.foo
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

  def testAmbiguousExtensionAndConversion2(): Unit = checkHasErrorAroundCaret(
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
        |object Test2 {
        |  trait Functor[F[_]]:
        |    extension [A, B](fa: F[A])
        |      def ffmap(f: A => B): F[B]
        |
        |  trait F[A]
        |  given fInt: F[Int] = ???
        |  given eitherFunctor[E: F]: Functor[[A] =>> Either[E, A]] with
        |    extension[A, B] (x: Either[E, A])
        |      def ffmap(f: A => B): Either[E, B] = x match
        |        case Left(err) => Left(err)
        |        case Right(a) => Right(f(a))
        |
        |  object A {
        |    val e1: Either[Int, Int] = Right(10)
        |    val e3 = e1.ffmap(a => a + 1)
        |  }
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

  def testAmbigous(): Unit = checkHasErrorAroundCaret(
    s"""
      |
      |trait Ordering[A]
      |object A {
      |  trait C
      |
      |  given Ordering[Int] with {
      |    extension (s: C) {
      |      def foo: Int = ???
      |    }
      |  }
      |
      |  class COps { def foo: Int = 123}
      |  given conv: Conversion[C, COps] = ???
      |
      |  val c: C = ???
      |  c.f${CARET}oo
      |}
      |""".stripMargin
  )

  def testConstructExtensionNeg(): Unit = checkHasErrorAroundCaret(
    s"""
       |object A {
       |  extension(x: Int)(using Int) { def foo: Int = 123 }
       |  123.fo${CARET}o
       |}
       |""".stripMargin
  )

  def testExtensionWithImplicitConversion(): Unit = checkTextHasNoErrors(
    """
      |object Wrapper {
      |  class MyClass
      |
      |  implicit def stringToMyClass(s: String): MyClass = new MyClass
      |
      |  extension (target: MyClass) {
      |    def extensionScala3Style(): String = target.toString
      |  }
      |
      |  val e: String = "42"
      |  e.extensionScala3Style() // BAD: IntelliJ: ERROR, Compiler: OK
      |}
      |""".stripMargin
  )

  // SCL-23876
  def testExtensionMethodsWithBackticks(): Unit = checkTextHasNoErrors(
    """
      |implicit class Blub(s: String) {
      |  def test1 = 3
      |  def `test2` = 3
      |}
      |
      |extension (str: String) def test3: Unit = ()
      |extension (str: String) def `test4`: Unit = ()
      |
      |"".test1    // ok
      |"".`test1`  // ok
      |
      |"".test2    // ok
      |"".`test2`  // ok
      |
      |"".test3    // ok
      |"".`test3`  // doesn't resolve
      |
      |"".test4    // doesn't resolve
      |"".`test4`  // ok
      |""".stripMargin
  )

  def testSCL24177(): Unit = checkTextHasNoErrors(
    """
      |
      |trait Preferences
      |
      |trait PrefReader[T] {
      |  def read(node: Preferences, name: String, default: T): T
      |}
      |
      |given PrefReader[Double] {
      |  def read(node: Preferences, name: String, default: Double): Double = ???
      |}
      |given PrefReader[String] {
      |  def read(node: Preferences, name: String, default: String): String = ???
      |}
      |
      |extension (node: Preferences) {
      |  def read[T: PrefReader](name: String): Option[T] = ???
      |}
      |
      |object Main {
      |  private lazy val node: Preferences = ???
      |
      |  private def loadHotfix(): String = {
      |    node.read[String]("hotfix").getOrElse("")
      |  }
      |
      |  def main(args: Array[String]): Unit = {
      |  }
      |}
      |""".stripMargin
  )

  def testSCL24167(): Unit = checkTextHasNoErrors(
    """
      |import scala.reflect.Typeable
      |
      |sealed trait EntityState
      |case class LeafState() extends EntityState
      |
      |object Main {
      |
      |  extension (all: List[EntityState]) {
      |    def collectWithFilter[T <: EntityState : Typeable](predicate: T => Boolean): List[T] = {
      |      all.collect { case e: T if predicate(e) => e }.toList
      |    }
      |  }
      |
      |  def main(args: Array[String]): Unit = {
      |    val l = List(LeafState(), LeafState(), LeafState())
      |    l.collectWithFilter[LeafState](_ => true)
      |  }
      |}
      |""".stripMargin
  )

  //SCL-22627
  def testSCL22627_1(): Unit = checkTextHasNoErrors(
    """object RootObject:
      |  type TypeAlias
      |  extension (self: TypeAlias) def show: String = ""
      |
      |  trait Trait
      |  extension (self: Trait) def show: String = ""
      |
      |trait RootTrait:
      |  type TypeAlias
      |  extension (self: TypeAlias) def show: String = ""
      |
      |  trait Trait
      |  extension (self: Trait) def show: String = ""
      |
      |//OK: statically-available types
      |object Usage1:
      |  import RootObject.TypeAlias
      |  val ta: TypeAlias = ???
      |  ta.show
      |
      |  import RootObject.Trait
      |  val t: Trait = ???
      |  t.show
      |
      |//ERROR: path-dependent types
      |object Usage2:
      |  val rt: RootTrait = ???
      |  import rt.TypeAlias
      |  val ta: TypeAlias = ???
      |  ta.show
      |
      |  import rt.Trait
      |  val t: Trait = ???
      |  t.show
      |""".stripMargin
  )

  def testSCL22627_2(): Unit = checkTextHasNoErrors(
    """import scala.quoted.{Expr, Quotes, quotes}
      |
      |object Example {
      |  def foo(using Quotes): Unit = {
      |    val qq = quotes
      |    import qq.reflect.*
      |
      |    //without this `.typeSymbol` is not resolved
      |    //import qq.reflect.TypeReprMethods //type TypeRepr
      |    //without this `.caseFields` is not resolved
      |    //import qq.reflect.SymbolMethods // type Symbol <: AnyRef
      |    //without this `.asExpr` is not resolved
      |    //import qq.reflect.TreeMethods // type Tree <: AnyRef
      |
      |    def extractFields(tpe: TypeRepr): List[(String, TypeRepr, List[Expr[Any]])] = {
      |      tpe.typeSymbol.caseFields.map { field =>
      |        val fieldType = tpe.memberType(field)
      |        val annotations = field.annotations.map(_.asExpr)
      |        field.name
      |        null
      |      }
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def testExtensionMethod_FromAutoDerivedTypeClass(): Unit = {
    checkTextHasNoErrors(
      """trait Functor[F[_]] {
        |  extension [A](fa: F[A]) def mapExt[B](f: A => B): F[B]
        |}
        |object Functor {
        |  def derived[F[_]]: Functor[F] = ???
        |}
        |
        |case class Bar[A](value: A) derives Functor
        |
        |object Usage {
        |  Bar(42).mapExt(_ + 1)
        |}
        |""".stripMargin
    )
  }

  def testExtensionMethod_FromExplicitGivenAliasTypeClass(): Unit = {
    checkTextHasNoErrors(
      """trait Functor[F[_]] {
        |  extension [A](fa: F[A]) def mapExt[B](f: A => B): F[B]
        |}
        |
        |case class Bar[A](value: A)
        |object Bar {
        |  given myFunctor: Functor[Bar] = ???
        |}
        |
        |object Usage {
        |  Bar(42).mapExt(_ + 1)
        |}
        |""".stripMargin
    )
  }

  def testExtensionMethod_FromExplicitGivenStructuralTypeClass(): Unit = {
    checkTextHasNoErrors(
      """trait Functor[F[_]] {
        |  extension [A](fa: F[A]) def mapExt[B](f: A => B): F[B]
        |}
        |
        |case class Bar[A](value: A)
        |
        |object Bar {
        |  given myFunctor: Functor[Bar] with
        |    extension [A](fa: Bar[A]) def mapExt[B](f: A => B): Bar[B] =
        |      Bar(f(fa.value))
        |}
        |
        |object Usage {
        |  Bar(42).mapExt(_ + 1)
        |}
        |""".stripMargin
    )
  }

  def testExtensionMethod_FromGenericAutoDerivedAliasTypeClass(): Unit = {
    checkTextHasNoErrors(
      """trait Functor[F[_]] {
        |  extension [A](fa: F[A]) def mapExt[B](f: A => B): F[B]
        |}
        |
        |object Functor {
        |  inline def derived[F[_]]: Functor[F] = ???
        |}
        |
        |case class Bar[Ctx, A](ctx: Ctx, value: A)
        |
        |object Bar {
        |  given derived$Functor[Ctx]: Functor[[A] =>> Bar[Ctx, A]] = ???
        |}
        |
        |object Usage {
        |  Bar("ctx", 42).mapExt(_ + 1)
        |}
        |""".stripMargin
    )
  }

  def testExtensionMethod_FromGenericExplicitGivenAliasTypeClass(): Unit = {
    checkTextHasNoErrors(
      """trait Functor[F[_]] {
        |  extension [A](fa: F[A]) def mapExt[B](f: A => B): F[B]
        |}
        |
        |case class Bar[Ctx, A](ctx: Ctx, value: A)
        |
        |object Bar {
        |  given myFunctor[Ctx]: Functor[[A] =>> Bar[Ctx, A]] = ???
        |}
        |
        |object Usage {
        |  Bar("ctx", 42).mapExt(_ + 1)
        |}
        |""".stripMargin
    )
  }

  def testExtensionMethod_FromGenericExplicitGivenStructuralTypeClass(): Unit = {
    checkTextHasNoErrors(
      """trait Functor[F[_]] {
        |  extension [A](fa: F[A]) def mapExt[B](f: A => B): F[B]
        |}
        |
        |case class Bar[Ctx, A](ctx: Ctx, value: A)
        |
        |object Bar {
        |  given myFunctor[Ctx]: Functor[[A] =>> Bar[Ctx, A]] with
        |    extension [A](fa: Bar[Ctx, A]) def mapExt[B](f: A => B): Bar[Ctx, B] =
        |      Bar(fa.ctx, f(fa.value))
        |}
        |
        |object Usage {
        |  Bar("ctx", 42).mapExt(_ + 1)
        |}
        |""".stripMargin
    )
  }
}
