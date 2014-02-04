package org.jetbrains.plugins.scala.lang.psi.types

import nonvalue.{TypeParameter, Parameter}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScParameter, ScParameterClause}
import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 02.06.2010
 */

//TODO must be abstract with no description when completed 
sealed class ApplicabilityProblem(val description: String = "unknown")

object ApplicabilityProblem {
  def unapply(a: ApplicabilityProblem): Option[String] = Some(a.description)
}

// definition syntax problems
case class MultipleDefinitions extends ApplicabilityProblem
case class MultipleDefinitionVariants extends ApplicabilityProblem
case class MalformedDefinition extends ApplicabilityProblem

// application syntax problems
case class PositionalAfterNamedArgument(argument: ScExpression) extends ApplicabilityProblem
//TODO , parameter
case class ParameterSpecifiedMultipleTimes(assignment: ScAssignStmt) extends ApplicabilityProblem
case class UnresolvedParameter(assignment: ScAssignStmt) extends ApplicabilityProblem
//TODO , parameter
case class ExpansionForNonRepeatedParameter(argument: ScExpression) extends ApplicabilityProblem
case class ElementApplicabilityProblem(element: PsiElement, actual: ScType, found: ScType) extends ApplicabilityProblem("42") //todo 

// applicability problem
case class DoesNotTakeParameters() extends ApplicabilityProblem
case class ExcessArgument(argument: ScExpression) extends ApplicabilityProblem
case class MissedParametersClause(clause: ScParameterClause) extends ApplicabilityProblem
case class MissedValueParameter(parameter: Parameter) extends ApplicabilityProblem
//TODO expectedType -> parameter
case class TypeMismatch(expression: ScExpression, expectedType: ScType) extends ApplicabilityProblem
case object WrongTypeParameterInferred extends ApplicabilityProblem

case object DoesNotTakeTypeParameters extends ApplicabilityProblem
case class ExcessTypeArgument(argument: ScTypeElement) extends ApplicabilityProblem
case class MissedTypeParameter(param: TypeParameter) extends ApplicabilityProblem
