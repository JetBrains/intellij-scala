package org.jetbrains.plugins.scala.lang.typeInference

class ImplicitParametersTest extends ImplicitParametersTestBase {
  def testSCL15862(): Unit = checkNoImplicitParameterProblems(
    s"""
       |class ImplicitDep()
       |class X {
       |  def callWithImplicitParam(implicit a: ImplicitDep): String = "test"
       |}
       |class TestA(x: X, private implicit val dep: ImplicitDep) {
       |  ${START}x.callWithImplicitParam$END
       |}
       |""".stripMargin
  )

  def testScalaJsUnionEvidence(): Unit = checkNoImplicitParameterProblems(
    s"""
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
       |  ${START}implicitly[Evidence[Seq[String], Seq[String]]]$END
       |
       |  implicit def base[A]: Evidence[A, A] =
       |    ReusableEvidence.asInstanceOf[Evidence[A, A]]
       |}
    """.stripMargin
  )

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

  def testSCL
}
