package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
sealed trait InvocationData {
  def typeResult: TypeResult

  def problems: Seq[ApplicabilityProblem] = Seq.empty

  def matched: Seq[(Parameter, ScExpression, ScType)] = Seq.empty

  def importsUsed: Set[ImportUsed] = Set.empty

  def implicitConversion: Option[ScalaResolveResult] = None

  def applyOrUpdateElem: Option[ScalaResolveResult] = None
}

object InvocationData {

  case class Full(inferredType: ScType,
                  override val problems: Seq[ApplicabilityProblem] = Seq.empty,
                  override val matched: Seq[(Parameter, ScExpression, ScType)] = Seq.empty,
                  override val importsUsed: Set[ImportUsed] = Set.empty,
                  override val implicitConversion: Option[ScalaResolveResult] = None,
                  override val applyOrUpdateElem: Option[ScalaResolveResult] = None) extends InvocationData {

    def withSubstitutedType: Option[Full] = (problems, matched) match {
      case (Seq(), Seq()) => Some(this)
      case (Seq(), seq) =>
        val map = seq.collect {
          case (parameter, _, scType) => parameter -> scType
        }.toMap
        val dependentSubst = ScSubstitutor(() => map)
        Some(copy(dependentSubst.subst(inferredType)))
      case _ => None
    }

    override def typeResult: TypeResult = Right(inferredType)
  }

  case class Plain(typeResult: Left[Failure, ScType]) extends InvocationData
}