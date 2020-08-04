package org.jetbrains.plugins.scala.failed.typeInference
import org.jetbrains.plugins.scala.lang.typeInference.ImplicitParametersTestBase

class FailingImplicitParametersTest extends ImplicitParametersTestBase {

  override protected def shouldPass = false

  def testScalaTestEmptiness(): Unit = checkNoImplicitParameterProblems (
    s"""
      |trait Emptiness[-T] {
      |  def isEmpty(thing: T): Boolean
      |}
      |
      |object Emptiness {
      |
      |  ${START}implicitly[Emptiness[Seq[String]]]$END
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

  def testAmbiguousImplicitWithExpectedType(): Unit = {
    checkNoImplicitParameterProblems(
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
         |  val x: Double = ${START}foldMap(list)(_.b)${END}
         |
         |}""".stripMargin)

  }

  def testExpectedTypeFromDifferentClause(): Unit = checkNoImplicitParameterProblems(
    s"""trait Test {
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
       |  bar(1)(X(1))(${START}threeClauses(1)(1L)${END})
       |}
    """.stripMargin
  )
}
