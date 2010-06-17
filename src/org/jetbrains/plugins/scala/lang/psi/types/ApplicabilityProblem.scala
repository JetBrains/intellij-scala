package org.jetbrains.plugins.scala.lang.psi.types

import nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}

/**
 * Pavel.Fatin, 02.06.2010
 */

// must be abstract with no description when completed 
sealed case class ApplicabilityProblem(description: String = "unknown")

// definition syntax problems
case class MultipleDefinitions extends ApplicabilityProblem
case class MultipleDefinitionVariants extends ApplicabilityProblem
case class MalformedDefinition extends ApplicabilityProblem

// application syntax problems
case class PositionalAfterNamedArgument(argument: ScExpression) extends ApplicabilityProblem
// , parameter
case class ParameterSpecifiedMultipleTimes(assignment: ScAssignStmt) extends ApplicabilityProblem
case class UnresolvedParameter(assignment: ScAssignStmt) extends ApplicabilityProblem
// , parameter
case class ExpansionForNonRepeatedParameter(argument: ScExpression) extends ApplicabilityProblem

// applicability problem
case class DoesNotTakeParameters extends ApplicabilityProblem
case class ExcessArgument(argument: ScExpression) extends ApplicabilityProblem
case class MissedParametersClause(clause: ScParameterClause) extends ApplicabilityProblem
case class MissedParameter(parameter: Parameter) extends ApplicabilityProblem
case class MissedImplicitParameter(parameter: Parameter) extends ApplicabilityProblem
// expectedType -> parameter
case class TypeMismatch(expression: ScExpression, expectedType: ScType) extends ApplicabilityProblem
