package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class ParameterizedTypeTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL7891() = {
    val text =
      """
        |trait TestTrait[A, B] { def foo(a: A): B }
        |class TestClass[A, B] extends TestTrait[A, B] { override def foo(a: A): B = ??? }
        |class Test {
        |  type Trait[B] = TestTrait[Test, B]
        |  type Cls[B] = TestClass[Test, B]
        |  object tc extends Cls[Any] { //Shows error "Wrong number of type parameters. Expected: 2, actual: 1"
        |      override def foo(a: Test): Any = ???
        |    }
        |  }
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL9014() = {
    val text =
      """
        |import scala.util.{Success, Try}
        |class Foo[A, In <: Try[A]] {
        |    def takeA(a: A) = a
        |    def takeIn(in: In) = {
        |      in match {
        |        case Success(a) ⇒ takeA(a) // cannot infer type
        |      }
        |    }
        |  }
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL10118() = {
    val text =
      """
        |object SCL10118{
        |
        |  trait Test[A]
        |
        |  case class First(i: Int) extends Test[String]
        |
        |  case class Second(s: String) extends Test[Int]
        |
        |  def run[A](t: Test[A]): A = t match {
        |    case First(i) => i.toString
        |    case Second(s) => s.length
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL10156() = {
    checkTextHasNoErrors(
      """  import scala.language.higherKinds
        |  import scala.language.reflectiveCalls
        |
        |  object Test {
        |
        |    trait Functor[F[_]] {
        |      def map[A, B](fa: F[A])(f: A => B): F[B]
        |    }
        |
        |    trait Applicative[F[_]] extends Functor[F] {
        |      self =>
        |      def apply[A, B](fab: F[A => B])(fa: F[A]): F[B] =
        |        map2(fab, fa)((ab, a) => ab(a))
        |
        |      def unit[A](a: => A): F[A]
        |
        |      def map[A, B](fa: F[A])(f: A => B): F[B] =
        |        apply[A, B](unit(f))(fa)
        |
        |      def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
        |        apply(apply[A, B => C](unit((a: A) => (b: B) => f(a, b)))(fa))(fb)
        |
        |      def compose[G[_]](G: Applicative[G]) =
        |        new Applicative[({type f[x] = F[G[x]]})#f] {
        |          override def unit[A](a: => A): F[G[A]] = self.unit(G.unit(a))
        |        }
        |    }
        |  }""".stripMargin
    )
  }

  def testSCL8031(): Unit = {
    checkTextHasNoErrors(
      """
         |import One.HList.:::
         |
         |object One extends App{
         |
         |  trait Fold[T, V] {
         |    type Apply[N <: T, Acc <: V] <: V
         |    def apply[N <: T, Acc <: V](n: N, acc: Acc): Apply[N, Acc]
         |  }
         |
         |  sealed trait HList {
         |    type Foldr[V, F <: Fold[Any, V], I <: V] <: V
         |    def foldr[V, F <: Fold[Any, V], I <: V](f: F, i: I): Foldr[V, F, I]
         |  }
         |
         |  final case class HCons[H, T <: HList](head: H, tail: T) extends HList {
         |    def ::[E](v: E) = HCons(v, this)
         |    type Foldr[V, F <: Fold[Any, V], I <: V] = F#Apply[H, tail.Foldr[V, F, I]]
         |    def foldr[V, F <: Fold[Any, V], I <: V](f: F, i: I): Foldr[V, F, I] =
         |      f(head, tail.foldr[V, F, I](f, i))
         |  }
         |
         |  object HList {
         |    type ::[H, T <: HList] = HCons[H, T]
         |    val :: = HCons
         |    type :::[A <: HList, B <: HList] = A#Foldr[HList, FoldHCons.type, B]
         |    implicit def concat[B <: HList](b: B): Concat[B] =
         |      new Concat[B] {
         |        def :::[A <: HList](a: A): A#Foldr[HList, FoldHCons.type, B] =
         |          a.foldr[HList, FoldHCons.type, B](FoldHCons, b)
         |      }
         |
         |    object FoldHCons extends Fold[Any, HList] {
         |      type Apply[N <: Any, H <: HList] = N :: H
         |      def apply[A, B <: HList](a: A, b: B) = HCons(a, b)
         |    }
         |  }
         |
         |  sealed trait Concat[B <: HList] { def :::[A <: HList](a: A): A ::: B }
         |}
      """.stripMargin.trim
    )
  }

  //this test is intermittent!
  def testSCL10399(): Unit = {
    val fileText =
      s"""
         |trait AA[T]
         |trait QQ[T] extends AA[T]
         |
         |class Z extends QQ[AnyRef]
         |
         |object Example {
         |  def asFA[F[_], A](fa: F[A]): F[A] = fa
         |
         |  val z: QQ[AnyRef] = asFA(new Z) // sometimes type of the rhs is AA[T] instead of QQ[T]
         |}
     """.stripMargin
    checkTextHasNoErrors(fileText)
  }

  def testSCL11597() = {
    checkTextHasNoErrors(
      """trait Node
        |
        |  class A(val a: Int, val b: Int) extends Node
        |
        |  class DefExtractorSimple[T <: Node, X](func: T => X) {
        |    def unapply(arg: T): Option[X] = Some(func(arg))
        |  }
        |
        |  def extractSimple[T <: Node, X](func: T => X): DefExtractorSimple[T, X] = new DefExtractorSimple[T, X](func)
        |
        |  val extractA = extractSimple((arg: A) => (arg.a, arg.b))
        |
        |  object ExtractAA {
        |    def unapply(arg: Node): Option[Int] = arg match {
        |      case extractA(a, _) => Some(a)
        |      case _ => None
        |    }
        |  }""".stripMargin
    )
  }

  def testSCL12656() = {
    checkTextHasNoErrors(
      """import scala.concurrent.{ExecutionContext, Future}
        |import scala.util.Success
        |
        |object TestCase {
        |  def f: Future[Any] = null
        |
        |  implicit class MyFuture[T](val f: Future[T]) {
        |    def awaitAndDo[U <: T](func: U => String)(implicit ec: ExecutionContext): String = {
        |      f onComplete {
        |        case Success(value) => return func(value.asInstanceOf[U])
        |        case _ => Unit
        |      }
        |      "bar"
        |    }
        |  }
        |
        |  private def foo = {
        |    implicit val ec: ExecutionContext = null
        |    var baz: String = f awaitAndDo[Option[String]] {
        |      case Some(s) => s
        |      case None => "oups"
        |    }
        |  }
        |}""".stripMargin
    )
  }

  def testSCL12908() = {
    val text =
      """
        |def check[T](array: Array[T]): Unit = {
        |    array match {
        |      case bytes: Array[Byte] =>
        |        println("Got bytes!")
        |      case _ =>
        |        println("Got something else than bytes!")
        |    }
        |  }
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL13042() = {
    val text =
      """
        |def f[R[_], T](fun: String => R[T]): String => R[T] = fun
        |val result = f(str => Option(str))
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL13089() = {
    val text =
      """
        |import scala.language.implicitConversions
        |import scala.language.higherKinds
        |trait Foo[T]
        |trait FooBuild[M[_], Face] {
        |  implicit def fromNum[T <: Face, Out <: T](value : T) : M[Out] = ???
        |}
        |implicit object Foo extends FooBuild[Foo, scala.Int]
        |def tryme[T](t : Foo[T]) = ???
        |tryme(40)
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
