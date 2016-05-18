package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

/**
 * Pavel.Fatin, 28.04.2010
 */

trait Strategy {
  def functionWithoutType(function: ScFunctionDefinition)

  def functionWithType(function: ScFunctionDefinition)

  def valueWithoutType(value: ScPatternDefinition)

  def valueWithType(value: ScPatternDefinition)

  def variableWithoutType(variable: ScVariableDefinition)

  def variableWithType(variable: ScVariableDefinition)

  def patternWithoutType(pattern: ScBindingPattern)

  def wildcardPatternWithoutType(pattern: ScWildcardPattern)

  def patternWithType(pattern: ScTypedPattern)

  def parameterWithoutType(param: ScParameter)

  def parameterWithType(param: ScParameter)
}
