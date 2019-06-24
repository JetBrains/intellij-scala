package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

class CatsHighlightingTest_2_12 extends ScalaHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_12

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader(
        "org.typelevel" %% "cats-core" % "1.5.0"
      )

  def testSCL14926(): Unit = {
    assertNothing(errorsFromScalaCode(
      """
        |import cats.Apply
        |import cats.~>
        |import cats.FlatMap
        |import cats.NonEmptyParallel
        |
        |object Test {
        |  /** [[cats.NonEmptyParallel]] instance for [[Observable]]. */
        |  implicit val observableNonEmptyParallel: NonEmptyParallel[Observable, CombineObservable.Type] =
        |    new NonEmptyParallel[Observable, CombineObservable.Type] {
        |      import CombineObservable.unwrap
        |      import CombineObservable.{apply => wrap}
        |
        |      override def flatMap: FlatMap[Observable] = ???
        |      override def apply: Apply[CombineObservable.Type] = CombineObservable.combineObservableApplicative
        |
        |      override val sequential = new (CombineObservable.Type ~> Observable) {
        |        def apply[A](fa: CombineObservable.Type[A]): Observable[A] = unwrap(fa)
        |      }
        |      override val parallel = new (Observable ~> CombineObservable.Type) {
        |        def apply[A](fa: Observable[A]): CombineObservable.Type[A] = wrap(fa)
        |      }
        |    }
        |
        |}
        |
        |abstract class Observable[+A]
        |
        |abstract class Newtype1[F[_]] { self =>
        |  type Base
        |  trait Tag extends Any
        |  type Type[+A] <: Base with Tag
        |
        |  def apply[A](fa: F[A]): Type[A] =
        |    fa.asInstanceOf[Type[A]]
        |
        |  def unwrap[A](fa: Type[A]): F[A] =
        |    fa.asInstanceOf[F[A]]
        |}
        |
        |object CombineObservable extends Newtype1[Observable] {
        |
        |  implicit val combineObservableApplicative: Apply[CombineObservable.Type] = new Apply[CombineObservable.Type] {
        |
        |    override def ap[A, B](ff: CombineObservable.Type[(A) => B])(fa: CombineObservable.Type[A]) = ???
        |
        |    override def map[A, B](fa: CombineObservable.Type[A])(f: A => B): CombineObservable.Type[B] = ???
        |
        |    override def map2[A, B, C](fa: CombineObservable.Type[A], fb: CombineObservable.Type[B])
        |                              (f: (A, B) => C): CombineObservable.Type[C] = ???
        |
        |    override def product[A, B](fa: CombineObservable.Type[A], fb: CombineObservable.Type[B]): CombineObservable.Type[(A, B)] = ???
        |  }
        |}
      """.stripMargin))
  }

  def testSCL15011(): Unit = assertNothing(errorsFromScalaCode(
    """
      |import cats.Monad
      |import cats.syntax.flatMap._
      |
      |class Test[F[_]: Monad] {
      |
      |  def first(fList: F[List[F[Unit]]]): F[Unit] = {
      |    fList.flatMap { list =>
      |      list.reduce(_ >> _)
      |    }
      |  }
      |
      |  type Alias = F[Unit]
      |
      |  def second(fList: F[List[Alias]]): F[Unit] = {
      |    fList.flatMap { list =>
      |      list.reduce(_ >> _)
      |    }
      |  }
      |}
    """.stripMargin
  ))

}
