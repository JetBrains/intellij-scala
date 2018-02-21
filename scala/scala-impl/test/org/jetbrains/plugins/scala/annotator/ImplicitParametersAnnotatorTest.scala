package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.template.ImplicitParametersAnnotator
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
class ImplicitParametersAnnotatorTest extends AnnotatorTestBase(ImplicitParametersAnnotator) {

  private def notFound(types: String*) = ImplicitParametersAnnotator.message(types)

  def testCorrectImplicits(): Unit = assertNothing(messages(
    """def implicitly[T](implicit e: T) = e
      |implicit val implicitInt = 42
      |val v1: Int = implicitly
      |val v2 = implicitly[Int]""".stripMargin
  ))

  def testUnresolvedImplicits(): Unit = assertMatches(messages("def implicitly[T](implicit e: T) = e; implicit val implicitInt = implicitly[Int]")) {
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

}
