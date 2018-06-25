package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.Assert
import org.junit.experimental.categories.Category

@Category(Array(classOf[PerfCycleTests]))
class ImplicitParametersTest extends TypeInferenceTestBase {

  override protected def shouldPass = false

  def checkNoImplicitParameterProblems(fileText: String): Unit = {
    val scalaFile: ScalaFile = configureFromFileText("dummy.scala",
      Some(ScalaLightCodeInsightFixtureTestAdapter.normalize(fileText)))
    val expr: ScExpression = findExpression(scalaFile)
    expr.findImplicitParameters match {
      case None =>
        Assert.fail("Expression with implicit parameters expected")
      case Some(seq) =>
        val hasProblems = seq.exists(_.isImplicitParameterProblem)
        if (shouldPass == hasProblems)
          Assert.fail("Problems in implicit parameters search: " + seq.mkString("\n"))
    }
  }

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
}
