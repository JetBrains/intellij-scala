package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

import scala.concurrent.Future

/**
  * Created by kate on 6/7/16.
  */
class ApplyTest extends ScalaLightCodeInsightFixtureTestAdapter {
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
}
