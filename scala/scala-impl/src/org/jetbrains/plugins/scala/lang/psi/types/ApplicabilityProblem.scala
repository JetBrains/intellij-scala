package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult


sealed abstract class ApplicabilityProblem

// definition syntax problems
case class MalformedDefinition(name: String) extends ApplicabilityProblem

// application syntax problems
case class PositionalAfterNamedArgument(argument: ScExpression) extends ApplicabilityProblem
//TODO , parameter
case class ParameterSpecifiedMultipleTimes(assignment: ScAssignment) extends ApplicabilityProblem
case class UnresolvedParameter(assignment: ScAssignment) extends ApplicabilityProblem
//TODO , parameter
case class ExpansionForNonRepeatedParameter(argument: ScExpression) extends ApplicabilityProblem

// applicability problem
case class DoesNotTakeParameters() extends ApplicabilityProblem
case class ExcessArgument(argument: ScExpression) extends ApplicabilityProblem
case class MissedParametersClause(clause: ScParameterClause) extends ApplicabilityProblem
case class MissedValueParameter(parameter: Parameter) extends ApplicabilityProblem
//TODO expectedType -> parameter
case class TypeMismatch(expression: ScExpression, expectedType: ScType) extends ApplicabilityProblem
case class DefaultTypeParameterMismatch(expectedType: ScType, actualType: ScType) extends ApplicabilityProblem
case object WrongTypeParameterInferred extends ApplicabilityProblem

case object DoesNotTakeTypeParameters extends ApplicabilityProblem
case class ExcessTypeArgument(argument: ScTypeElement) extends ApplicabilityProblem
case class MissedTypeParameter(param: TypeParameter) extends ApplicabilityProblem
case object ExpectedTypeMismatch extends ApplicabilityProblem
case class NotFoundImplicitParameter(tpe: ScType) extends ApplicabilityProblem
case class AmbiguousImplicitParameters(resuts: collection.Seq[ScalaResolveResult]) extends ApplicabilityProblem

// TODO AmbiguousOverloading(results: Seq[ScalaResolveResult]) extends ApplicabilityProblem ?

case class IncompleteCallSyntax(description: String) extends ApplicabilityProblem
case class InternalApplicabilityProblem(description: String) extends ApplicabilityProblem
