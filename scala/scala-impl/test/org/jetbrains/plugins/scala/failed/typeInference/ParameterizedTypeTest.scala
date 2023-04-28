package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class ParameterizedTypeTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def shouldPass: Boolean = false

  def testSCL9014(): Unit = {
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
}
