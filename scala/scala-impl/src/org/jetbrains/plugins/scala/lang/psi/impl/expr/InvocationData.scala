package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, DoesNotTakeParameters, ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
sealed trait InvocationData {
  def typeResult: TypeResult
  def problems: Seq[ApplicabilityProblem]
  def matchedParams: Seq[(Parameter, ScExpression)]
  def matchedTypes: Seq[(Parameter, ScType)]
  def importsUsed: collection.Set[ImportUsed]
  def implicitConversion: Option[ScalaResolveResult]
  def applyOrUpdateElem: Option[ScalaResolveResult]
}

object InvocationData {

  def noSuitableParameters(inferredType: ScType, result: ScalaResolveResult): Full = {
    InvocationData.Full(
      inferredType,
      Seq(new DoesNotTakeParameters),
      Seq.empty,
      Seq.empty,
      result.importsUsed,
      result.implicitConversion,
      None)
  }

  case class Full(inferredType: ScType,
                  problems: Seq[ApplicabilityProblem] = Seq.empty,
                  matchedParams: Seq[(Parameter, ScExpression)] = Seq.empty,
                  matchedTypes: Seq[(Parameter, ScType)] = Seq.empty,
                  importsUsed: collection.Set[ImportUsed] = Set.empty,
                  implicitConversion: Option[ScalaResolveResult] = None,
                  applyOrUpdateElem: Option[ScalaResolveResult] = None) extends InvocationData {

    def withApplyUpdate(result: ScalaResolveResult): Full = copy(
      importsUsed = result.importsUsed,
      implicitConversion = result.implicitConversion,
      applyOrUpdateElem = Some(result)
    )

    def withSubstitutedType: Option[Full] = {
      if (problems.nonEmpty) None
      else Some {
        if (matchedTypes.isEmpty) this
        else {
          val dependentSubst = ScSubstitutor(() => matchedTypes.toMap)
          copy(dependentSubst.subst(inferredType))
        }
      }
    }

    override def typeResult: TypeResult = Right(inferredType)
  }

  case class Plain(typeResult: TypeResult) extends InvocationData {
    def problems: Seq[ApplicabilityProblem] = Seq.empty
    def matchedParams: Seq[(Parameter, ScExpression)] = Seq.empty
    def matchedTypes: Seq[(Parameter, ScType)] = Seq.empty
    def importsUsed: collection.Set[ImportUsed] = Set.empty
    def implicitConversion: Option[ScalaResolveResult] = None
    def applyOrUpdateElem: Option[ScalaResolveResult] = None
  }
}