package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.template.ImplicitParametersAnnotator
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
abstract class ImplicitParametersAnnotatorTestBase extends AnnotatorTestBase(ImplicitParametersAnnotator) {
  protected def notFound(types: String*) = ImplicitParametersAnnotator.message(types)
}

class ImplicitParametersAnnotatorTest extends ImplicitParametersAnnotatorTestBase {

  def testCorrectImplicits(): Unit = assertNothing(messages(
    """def implicitly[T](implicit e: T) = e
      |implicit val implicitInt = 42
      |val v1: Int = implicitly
      |val v2 = implicitly[Int]""".stripMargin
  ))

  def testUnresolvedImplicits(): Unit = assertMatches(messages(
    """def implicitly[T](implicit e: T) = e
      |implicit val implicitInt = implicitly[Int]""".stripMargin)) {
    case Error("implicitly[Int]", m) :: Nil if m == notFound("Int") =>
  }

  def testPair(): Unit = assertMatches(messages("def foo(implicit i: Int, d: Double) = (i, d); foo")) {
    case Error("foo", m) :: Nil if m == notFound("Int", "Double") =>
  }

  def testInfix(): Unit = assertNothing(messages(
    //adapted from scalatest
    """
      |trait Test {
      |  def nothing: Nothing
      |
      |  trait Equality[T]
      |
      |  abstract class MatcherFactory1[-SC, TC1[_]]
      |
      |  def should[TYPECLASS1[_]](rightMatcherFactory1: MatcherFactory1[Int, TYPECLASS1])
      |                           (implicit typeClass1: TYPECLASS1[Int]): Unit = nothing
      |
      |  implicit def default[T]: Equality[T] = nothing
      |
      |  def equal(right: Int): MatcherFactory1[Int, Equality[Int]] = nothing
      |
      |  this should equal(42)
      |}""".stripMargin)
  )

  //adapted from scalacheck usage in finch
  def testCombineEquivBounds(): Unit = assertNothing(messages(
    """
      |object collection {
      |  trait Seq[X]
      |}
      |
      |trait Test {
      |  def nothing: Nothing
      |
      |  trait Cogen[T]
      |
      |  type Seq[X] = collection.Seq[X]
      |
      |  object Cogen extends CogenLowPriority {
      |    def apply[T](implicit ev: Cogen[T], dummy: Cogen[T]): Cogen[T] = ev
      |
      |    implicit def cogenInt: Cogen[Int] = nothing
      |  }
      |
      |  trait CogenLowPriority {
      |    implicit def cogenSeq[CC[x] <: Seq[x], A: Cogen]: Cogen[CC[A]] = nothing
      |  }
      |
      |  def foo(implicit c: Cogen[Seq[Int]]) = nothing
      |
      |  foo
      |}
    """.stripMargin
  ))

  def testImplicitCollectorCacheBug(): Unit = {
    val actualMessages = messages(
      """
        |implicit def int: Int = 1
        |implicit def bool(implicit int: Int): Boolean = true
        |
        |def foo(j: Int)(implicit b: Boolean) = j
        |
        |def test(): Unit = {
        |  foo(1)
        |
        |  {
        |    implicit val secondInt: Int = 2
        |    foo(2)
        |  }
        |
        |  foo(3)
        |}
      """.stripMargin).get
    assertMessages(Error("foo(2)", notFound("Boolean")) :: Nil)(actualMessages)
  }

  def testImplicitsInConstructorsMissing(): Unit = {
    val actualMessages = messages(
      """object Test {
        |
        |  class MyClass(implicit number: Int)
        |
        |  class MyClassWithArgs(x: Int)(implicit number: Int)
        |
        |  class MySecondClass extends MyClass()
        |
        |  class MyThirdClass extends MyClassWithArgs(42)
        |
        |  new MyClass()
        |  new MyClass
        |  new MyClassWithArgs(43)
        |}
        |""".stripMargin
    ).get

    assertMessages(List(
      Error("MyClass()",           notFound("Int")),
      Error("MyClassWithArgs(42)", notFound("Int")),
      Error("MyClass()",           notFound("Int")),
      Error("MyClass",             notFound("Int")),
      Error("MyClassWithArgs(43)", notFound("Int"))
    ))(actualMessages)
  }

  def testImplicitsInConstructors(): Unit = {
    assertNothing(messages(
      """object Test {
        |  implicit val i: Int = 0
        |
        |  class MyClass(implicit number: Int)
        |
        |  class MyClassWithArgs(x: Int)(implicit number: Int)
        |
        |  class MySecondClass extends MyClass()
        |
        |  class MyThirdClass extends MyClassWithArgs(42)
        |
        |  new MyClass()
        |  new MyClass
        |  new MyClassWithArgs(43)
        |}
        |""".stripMargin
    ).get)
  }

  def testApplyAfterConstructor(): Unit = {
    val actualMessages = messages(
      """|object A {
         |
         |  class B
         |  class MyClass {
         |    def apply(i: Int)(implicit b: B) = b
         |  }
         |
         |  new MyClass()(2)
         |}
         |""".stripMargin).get
    assertMessages(Error("new MyClass()(2)", notFound("B")) :: Nil)(actualMessages)
  }

  def testImplicitBefore(): Unit = {
    val implicitAbstract = messages(
      """
        |object ImplicitAbstract {
        |  def bar(implicit i: Int) = i
        |  trait Actor { implicit def context: Int }
        |  trait Stash { def context: Int = 1 }
        |  trait ActorImpl extends Stash with Actor { bar }
        |}
      """.stripMargin
    )
    val bothAbstract = messages(
      """
        |object BothAbstract {
        |  def bar(implicit i: Int) = i
        |  trait Actor { implicit def context: Int }
        |  trait Stash { def context: Int }
        |  trait ActorImpl extends Stash with Actor { bar }
        |}
      """.stripMargin
    )
    val implicitConcrete = messages(
      """
        |object ImplicitConcrete {
        |  def bar(implicit i: Int) = i
        |  trait Actor { implicit def context: Int = 1 }
        |  trait Stash { def context: Int }
        |  trait ActorImpl extends Stash with Actor { bar }
        |}
      """.stripMargin
    )

    assertNothing(implicitConcrete)
    assertNothing(bothAbstract)

    assertMessages(implicitAbstract.get)(Error("bar", notFound("Int")) :: Nil)
  }


  def testNotImplicitBefore(): Unit = {
    val bothAbstract = messages(
      """
        |object BothAbstract {
        |  def bar(implicit i: Int) = i
        |  trait Actor { implicit def context: Int }
        |  trait Stash { def context: Int }
        |  trait ActorImpl extends Actor with Stash { bar }
        |}
      """.stripMargin
    )
    val implicitAbstract = messages(
      """
        |object ImplicitAbstract {
        |  def bar(implicit i: Int) = i
        |  trait Actor { implicit def context: Int }
        |  trait Stash { def context: Int = 1 }
        |  trait ActorImpl extends Actor with Stash { bar }
        |}
      """.stripMargin
    )
    val implicitConcrete = messages(
      """
        |object ImplicitConcrete {
        |  def bar(implicit i: Int) = i
        |  trait Actor { implicit def context: Int = 1 }
        |  trait Stash { def context: Int }
        |  trait ActorImpl extends Actor with Stash { bar }
        |}
      """.stripMargin
    )

    assertNothing(implicitConcrete)

    val error = Error("bar", notFound("Int")) :: Nil

    assertMessages(bothAbstract.get)(error)
    assertMessages(implicitAbstract.get)(error)
  }

  def testRecursiveFunctionSeveralImplicits(): Unit = {
    val text =
      """
        |object Bug {
        |  trait Monad[F[_]]
        |  trait Console[F[_]]
        |  trait Random[F[_]]
        |
        |  def gameLoop[F[_]](implicit m: Monad[F], c: Console[F], r: Random[F]): F[Unit] = gameLoop
        |
        |  def gameLoop2[F[_]: Monad : Console : Random]: F[Unit] = gameLoop2
        |}
      """.stripMargin
    assertNothing(messages(text))
  }
}

//annotator tests doesn't have scala library, so it's not possible to use FunctionType, for example
class ImplicitParametersAnnotatorHeavyTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testExpectedTypeFromDifferentClause(): Unit = checkTextHasNoErrors(
    """trait Test {
      |  def nothing: Nothing
      |
      |  case class X[T](t: T)
      |  class Z
      |
      |  trait Show[T]
      |
      |  object Show {
      |    implicit val showLong: Show[Long] = nothing
      |    implicit val showInt : Show[Int]  = nothing
      |  }
      |
      |  def oneClause[A : Show](v: A)                    : Z = new Z
      |  def twoClauses[A : Show](k: Int)(v: A)           : Z = new Z
      |  def twoArguments[A : Show](k: Int, v: A)         : Z = new Z
      |  def threeClauses[A : Show](k: Int)(l: Long)(v: A): Z = new Z
      |
      |  def foo[A](x: X[A])(f: A => Z): Unit = {}
      |
      |  def bar[A](k: Int)(x: X[A])(f: A => Z): Unit = {}
      |
      |  foo(X(1))(oneClause)
      |  foo(X(1))(twoClauses(1))
      |  foo(X(1))(twoArguments(1, _))
      |  foo(X(1))(threeClauses(1)(1L))
      |
      |  bar(1)(X(1))(oneClause)
      |  bar(1)(X(1))(twoClauses(1))
      |  bar(1)(X(1))(twoArguments(1, _))
      |  bar(1)(X(1))(threeClauses(1)(1L))
      |}
    """.stripMargin
  )

  def testSpecificityFromSbtDsl(): Unit = checkTextHasNoErrors(
    """object Test {
      |  object Append extends scala.AnyRef {
      |
      |    sealed trait Value[A, B] extends scala.AnyRef {
      |      def appendValue(a : A, b : B) : A
      |    }
      |    sealed trait Sequence[A, -B, T] extends scala.AnyRef with Append.Value[A, T] with Append.Values[A, B]
      |
      |    sealed trait Values[A, -B] extends scala.AnyRef {
      |      def appendValues(a : A, b : B) : A
      |    }
      |
      |    implicit def appendSeq[T, V <: T] : Append.Sequence[scala.Seq[T], scala.Seq[V], V] = ???
      |    implicit def appendSeqImplicit[T, V](implicit evidence$1 : scala.Function1[V, T]) : Append.Sequence[scala.Seq[T], scala.Seq[V], V] = ???
      |  }
      |
      |  class SettingKey[T] {
      |    def +=[U](v : U)(implicit a : Append.Value[T, U]) : SettingKey[T] = ???
      |  }
      |
      |  val key = new SettingKey[Seq[String]]
      |
      |  key += "someString"
      |}
    """.stripMargin
  )

  def testScalaTestEmptiness(): Unit = checkTextHasNoErrors (
    """
      |trait Emptiness[-T] {
      |  def isEmpty(thing: T): Boolean
      |}
      |
      |object Emptiness {
      |
      |  implicitly[Emptiness[Seq[String]]]
      |
      |  import scala.language.higherKinds
      |
      |  implicit def emptinessOfGenTraversable[E, TRAV[e] <: scala.collection.GenTraversable[e]]: Emptiness[TRAV[E]] =
      |    new Emptiness[TRAV[E]] {
      |      def isEmpty(trav: TRAV[E]): Boolean = trav.isEmpty
      |    }
      |
      |  import scala.language.reflectiveCalls
      |
      |  implicit def emptinessOfAnyRefWithIsEmptyMethod[T <: AnyRef { def isEmpty(): Boolean}]: Emptiness[T] =
      |    new Emptiness[T] {
      |      def isEmpty(obj: T): Boolean = obj.isEmpty
      |    }
      |
      |  implicit def emptinessOfAnyRefWithParameterlessIsEmptyMethod[T <: AnyRef { def isEmpty: Boolean}]: Emptiness[T] =
      |    new Emptiness[T] {
      |      def isEmpty(obj: T): Boolean = obj.isEmpty
      |    }
      |}
    """.stripMargin)

  def testScalaJsUnionEvidence(): Unit = checkTextHasNoErrors(
    """
      |sealed trait Evidence[-A, +B]
      |
      |private object ReusableEvidence extends Evidence[scala.Any, scala.Any]
      |
      |abstract sealed class EvidenceLowestPrioImplicits {
      |
      |  implicit def covariant[F[+ _], A, B](implicit ev: Evidence[A, B]): Evidence[F[A], F[B]] =
      |    ReusableEvidence.asInstanceOf[Evidence[F[A], F[B]]]
      |
      |  implicit def contravariant[F[- _], A, B](implicit ev: Evidence[B, A]): Evidence[F[A], F[B]] =
      |    ReusableEvidence.asInstanceOf[Evidence[F[A], F[B]]]
      |}
      |
      |object Evidence extends EvidenceLowestPrioImplicits {
      |
      |  implicitly[Evidence[Seq[String], Seq[String]]]
      |
      |  implicit def base[A]: Evidence[A, A] =
      |    ReusableEvidence.asInstanceOf[Evidence[A, A]]
      |}
    """.stripMargin
  )

  def testConstructorTypeInference(): Unit = checkTextHasNoErrors {
    """
      |class Printer {
      |
      |  implicit val intPrinter = new Printable[Int] { def print(i: Int): String = null }
      |
      |  val lspb = new GetLoggerSpecParamBounded(3)
      |}
      |
      |trait Printable[A] { def print(a: A): String }
      |class GetLoggerSpecParamBounded[A, B <: Printable[A]](default: A)(implicit  printer: B) {
      |  def print = printer.print(default)
      |}
      |class String
    """.stripMargin
  }

  def testSCL14180(): Unit = checkTextHasNoErrors {
    """
      |case class Boxing[-A, +B](fun: A => B) extends AnyVal
      |
      |object Boxing extends LowPrioBoxing {
      |  def fromImplicitConv[A, B](implicit conv: A => B): Boxing[A, B] = Boxing(conv)
      |
      |  implicit val IntBoxing: Boxing[Int, java.lang.Integer] = fromImplicitConv
      |}
      |trait LowPrioBoxing { this: Boxing.type =>
      |  implicit def nullableBoxing[A >: Null]: Boxing[A, A] = Boxing(identity)
      |}
      |
      |final class Opt[+A] private(private val rawValue: Any) extends AnyVal with Serializable {
      |  def boxedOrNull[B >: Null](implicit boxing: Boxing[A, B]): B = ???
      |}
      |
      |object Opt {
      |  def apply[A](value: A): Opt[A] = ???
      |}
      |
      |object Test {
      |  val jint: java.lang.Integer = Opt(41).boxedOrNull
      |}
    """.stripMargin

  }

  def testNotFoundImplicitWithExpectedType(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Z {
         |  trait Monoid[T]
         |
         |  case class A(a: String, b: Int)
         |
         |  val list: List[A] = List(A("", 1))
         |
         |  implicit val intMonoid: Monoid[Int] = null
         |
         |  implicit def intToString(i: Int): String = null
         |
         |  def foldMap[A, B](list: List[A])(f: A => B)(implicit m: Monoid[B]): B = f(list.head)
         |
         |  val x: Double = foldMap(list)(_.b)
         |  val y: String = foldMap(list)(_.b)
         |
         |}""".stripMargin)
  }

  def testAmbiguousImplicitWithExpectedType(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Z {
         |  trait Monoid[T]
         |
         |  case class A(a: String, b: Int)
         |
         |  val list: List[A] = List(A("", 1))
         |
         |  implicit val intMonoid: Monoid[Int] = null
         |
         |  implicit val doubleMonoid1: Monoid[Double] = null
         |  implicit val doubleMonoid2: Monoid[Double] = null
         |
         |  implicit def intToString(i: Int): String = null
         |
         |  def foldMap[A, B](list: List[A])(f: A => B)(implicit m: Monoid[B]): B = f(list.head)
         |
         |  val x: Double = foldMap(list)(_.b)
         |
         |}""".stripMargin)

  }


}

class ImplicitParameterFailingTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass = false

  def test_t6948(): Unit = checkTextHasNoErrors(
    """
      |import scala.collection.generic.CanBuildFrom
      |
      |object Test {
      |
      |  def shuffle[T, CC[X] <: TraversableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T] = ???
      |  val range: Range.Inclusive = ???
      |
      |  def a1 = shuffle(range)
      |}
    """.stripMargin)
}

class ImplicitParametersAnnotatorFailingTest extends ImplicitParametersAnnotatorTestBase {

  override protected def shouldPass = false

  def testSCL5472C(): Unit = {
    val text =
      """
        |class List[+A]
        |object List {
        |  def apply[A](xs: A*): List[A] = new List[A]()
        |}
        |
        |case class Tuple2[+A, +B]    (a: A, b: B)
        |case class Tuple3[+A, +B, +C](a: A, b: B, c: C)
        |
        |trait SCL5472C {
        |  def nothing: Nothing
        |
        |  class ParamDefAux[T]
        |
        |  implicit def forTuple[T](implicit x: ParamDefAux[Tuple3[T, T, T]]): ParamDefAux[Tuple2[T, T]] =
        |    new ParamDefAux
        |
        |  implicit def forTuple                                             : ParamDefAux[Tuple2[Double, Double]] =
        |    new ParamDefAux
        |
        |  implicit def forTriple[T]                                         : ParamDefAux[Tuple3[T, T, T]] =
        |    new ParamDefAux
        |
        |  implicit val x: List[Int] = List(1, 2, 3)
        |
        |  def foo[T, S](implicit t: List[T], x: ParamDefAux[Tuple2[T, S]]): S = nothing
        |
        |  foo
        |}
      """.stripMargin
    assertMessages(Error("foo", notFound("ParamDefAux[Tuple2[Int, S_]]")) :: Nil)(messages(text).get)
  }
}
