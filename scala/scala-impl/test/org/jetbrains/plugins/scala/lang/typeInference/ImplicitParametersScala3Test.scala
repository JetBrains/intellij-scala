package org.jetbrains.plugins.scala.lang.typeInference
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ImplicitParametersScala3Test extends ImplicitParametersTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6

  def testSCL21117(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Main:
       |  trait T0 { def foo0: String = ??? }
       |  trait T1 extends T0 { def foo1: String = ??? }
       |  trait T2 extends T0 { def foo2: String = ??? }
       |
       |  implicit val b: T1 & T2 = new T1 with T2 {}
       |
       |object Other1:
       |  import Main.*
       |  summon[T1 | T2]
       |  summon[T1 & T2]
       |
       |object Other2:
       |  ${START}summon[Main.T1 | Main.T2]$END
       |""".stripMargin
  )

  def testSCL21117_2(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object Main:
       |  trait T0 { def foo0: String = ??? }
       |  trait T1 extends T0 { def foo1: String = ??? }
       |  trait T2 extends T0 { def foo2: String = ??? }
       |
       |  implicit val b: T1 & T2 = new T1 with T2 {}
       |
       |object Other2:
       |  ${START}summon[Main.T1 & Main.T2]$END
       |""".stripMargin
  )

  def testSCL21488(): Unit = checkNoImplicitParameterProblems(
    s"""
       |object A {
       |  class Constraint[A, C]
       |  class UnionConstraint[A, C] extends Constraint[A, C]
       |  class IsUnion[A]
       |  class True
       |
       |  given[A]                        : Constraint[A, True] with {}
       |  given[A, C](using u: IsUnion[C]): UnionConstraint[A, C] = ???
       |  given[A]                        : IsUnion[A] = ???
       |
       |  ${START}summon[Constraint[String, True]]$END
       |}
       |""".stripMargin
  )

  def testSCL20670(): Unit = checkTextHasNoErrors(
    s"""
       |trait CaseClassName[A]:
       |  def get: String
       |
       |object CaseClassName:
       |  def derived[A](using a: scala.deriving.Mirror.Of[A]): CaseClassName[A] = new CaseClassName[A]:
       |    def get: String = a.toString
       |case class CoolClass(i: Int) derives CaseClassName
       |""".stripMargin
  )

  def testMirrorOfSealed(): Unit = checkNoImplicitParameterProblems(
    s"""
       |sealed trait Foo
       |case class Bar extends Foo
       |case object Baz extends Foo
       |sealed class Qux extends Foo
       |enum F extends Qux
       |
       |object A {
       |  ${START}implicitly[scala.deriving.Mirror.Of[Foo]]$END
       |}
       |""".stripMargin
  )

  def testMirrorNeg(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |trait Foo
         |case class Bar extends Foo
         |object Test { imp${CARET}licitly[scala.deriving.Mirror.Of[Foo]] }
         |""".stripMargin
    )

    checkHasErrorAroundCaret(
      s"""
         |sealed trait Foo
         |class Bar extends Foo
         |object Test { imp${CARET}licitly[scala.deriving.Mirror.Of[Foo]] }
         |""".stripMargin
    )
  }

  def testPolyFunctionContextBound(): Unit = checkNoImplicitParameterProblems(
    s"""
      |trait Ord[A]
      |def compare[A: Ord](x: A, y: A): Int = ???
      |val comparer = [X: Ord as o] => (x: X, y: X) => ${START}compare(x, y)$END
      |""".stripMargin
  )

  def testNewStyleGivenParameters1(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Ord[A] { def compare(x: A, y: A): Int }
       |given [A] => Ord[A] => Ord[List[A]] {
       |  override def compare(xs: List[A], ys: List[A]): Int = {
       |    ${START}summon[Ord[A]]$END
       |    1
       |  }
       |}
       |""".stripMargin
  )

  def testNewStyleGivenParameters2(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Ord[A] { def compare(x: A, y: A): Int }
       |given [A] => (ord: Ord[A]) => Ord[List[A]] {
       |  override def compare(xs: List[A], ys: List[A]): Int = {
       |    ${START}summon[Ord[A]]$END
       |    1
       |  }
       |}
       |""".stripMargin
  )

  def testNewStyleGivenParameters3(): Unit = checkNoImplicitParameterProblems(
    s"""
       |trait Ord[A] { def compare(x: A, y: A): Int }
       |given [A: Ord as ord] => Ord[List[A]] {
       |  override def compare(xs: List[A], ys: List[A]): Int = {
       |    ${START}summon[Ord[A]]$END
       |    1
       |  }
       |}
       |""".stripMargin
  )
}
