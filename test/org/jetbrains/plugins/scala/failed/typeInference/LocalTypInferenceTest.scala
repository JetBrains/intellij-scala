package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 28/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class LocalTypInferenceTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9671(): Unit = doTest {
    """
      |object SCL9671 {
      |  class U
      |  class TU extends U
      |  class F[T <: U]
      |  class A[T <: U](x: F[T], y: Set[T] = Set.empty[T])
      |
      |  val f: F[TU] = new F
      |  /*start*/new A(f)/*end*/
      |}
      |//SCL9671.A[SCL9671.TU]
    """.stripMargin.trim
  }

  def testSCL5809(): Unit = doTest {
    """
      |object SCL5809 {
      |
      |  trait Functor[F[_]] {
      |    def map[A, B](fa: F[A])(f: A => B): F[B]
      |  }
      |
      |  trait Applicative[F[_]] extends Functor[F] {
      |    def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C]
      |
      |    def apply[A, B](fab: F[A => B])(fa: F[A]): F[B]
      |
      |    def unit[A](a: A): F[A]
      |
      |    // Excercise 1
      |    def map2ApplyUnit[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] = {
      |      apply[B, C](apply[A, B => C](unit(f.curried))(fa))(fb)
      |    }
      |
      |    def applyMap2Unit[A, B](fab: F[A => B])(fa: F[A]): F[B] = {
      |      map2(fab, fa)((f, a) => f(a))
      |    }
      |
      |    def map[A, B](fa: F[A])(f: A => B): F[B] = {
      |      apply(/*start*/unit(f)/*end*/)(fa)
      |    }
      |  }
      |
      |  object Applicative {
      |  }
      |
      |}
      |//F[A => B]
    """.stripMargin.trim
  }

  def testSCL6482(): Unit = doTest {
    """
      |object SCL6482 {
      |  class Foo[T, U <: T](u: U)
      |  def foo[T](t: T) = new Foo(t)
      |
      |  /*start*/foo(1)/*end*/
      |}
      |//SCL6482.Foo[Int, Int]
    """.stripMargin.trim
  }

  def testSCL7970(): Unit = doTest(
    """
      |trait Set[-A]{
      |  private val self = this
      |
      |  def contains(e: A): Boolean
      |
      |  def x[B](other: Set[B]): Set[(A, B)] = new Set[(A, B)] {
      |    override def contains(e: (A, B)): Boolean = (self /*start*/contains/*end*/ e._1) && (other contains e._2)
      |  }
      |}
      |//(A) => Boolean
    """.stripMargin)
}
