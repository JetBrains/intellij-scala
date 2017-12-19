package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, ScSubstitutor, ScType}
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
  def implicitFunction: Option[ScalaResolveResult]
  def applyOrUpdateElem: Option[ScalaResolveResult]
}

object InvocationData {

  case class Success(inferredType: ScType,
                     problems: Seq[ApplicabilityProblem] = Seq.empty,
                     matchedParams: Seq[(Parameter, ScExpression)] = Seq.empty,
                     matchedTypes: Seq[(Parameter, ScType)] = Seq.empty,
                     importsUsed: collection.Set[ImportUsed] = Set.empty,
                     implicitFunction: Option[ScalaResolveResult] = None,
                     applyOrUpdateElem: Option[ScalaResolveResult] = None) extends InvocationData {

    def withApplyUpdate(data: InvocationData): Success = copy(
      importsUsed = data.importsUsed,
      implicitFunction = data.implicitFunction,
      applyOrUpdateElem = data.applyOrUpdateElem
    )

    def withSubstitutedType: Option[Success] = {
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

  object Success {
    def fromApplyUpdate[T <: ScType](inferredType: T, applyOrUpdateElem: Option[ScalaResolveResult]): Success = {
      val importsUsed = applyOrUpdateElem.map(_.importsUsed).getOrElse(collection.Set.empty)
      val implicitFunction = applyOrUpdateElem.flatMap(_.implicitConversion)

      Success(inferredType, Seq.empty, Seq.empty, Seq.empty, importsUsed, implicitFunction, applyOrUpdateElem)
    }
  }

  case class ApplyOrUpdate(inferredType: ScType, result: Option[ScalaResolveResult]) extends InvocationData {
    override def typeResult: TypeResult = Right(inferredType)
    override def importsUsed: collection.Set[ImportUsed] = result.map(_.importsUsed).getOrElse(Set.empty)
    override def implicitFunction: Option[ScalaResolveResult] = result.flatMap(_.implicitConversion)
    override def applyOrUpdateElem: Option[ScalaResolveResult] = result
    override def problems: Seq[ApplicabilityProblem] = Seq.empty
    override def matchedParams: Seq[(Parameter, ScExpression)] = Seq.empty
    override def matchedTypes: Seq[(Parameter, ScType)] = Seq.empty
  }

  case class Empty(typeResult: TypeResult) extends InvocationData {
    def problems: Seq[ApplicabilityProblem] = Seq.empty
    def matchedParams: Seq[(Parameter, ScExpression)] = Seq.empty
    def matchedTypes: Seq[(Parameter, ScType)] = Seq.empty
    def importsUsed: collection.Set[ImportUsed] = Set.empty
    def implicitFunction: Option[ScalaResolveResult] = None
    def applyOrUpdateElem: Option[ScalaResolveResult] = None
  }
}