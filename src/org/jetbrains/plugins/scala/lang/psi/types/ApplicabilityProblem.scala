package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameters, ScParameterClause, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Pavel.Fatin, 02.06.2010
 */

sealed class ApplicabilityProblem 

// function definition problems
case class MultipleDefinitions(functions: Seq[ScFunction]) extends ApplicabilityProblem
case class MultipleDefinitionVariants(functions: Seq[ScFunction]) extends ApplicabilityProblem
case class MalformedDefinition extends ApplicabilityProblem

// call syntax problems
case class PositionAlfterNamedArguemnt extends ApplicabilityProblem
case class ParameterSpecifiedMultipleTimes extends ApplicabilityProblem

// call applicability problem
case class DoesNotTakeParameters extends ApplicabilityProblem
case class ExcessArguments(arguments: Seq[ScExpression]) extends ApplicabilityProblem
case class MissedParameterClauses(clause: Seq[ScParameterClause]) extends ApplicabilityProblem
case class MissedParameters(parameter: Seq[ScParameter]) extends ApplicabilityProblem
case class MissedImplicitParameters(parameter: Seq[ScParameter]) extends ApplicabilityProblem
case class TypeMismatch(pairs: Seq[(ScParameter, ScExpression)]) extends ApplicabilityProblem
