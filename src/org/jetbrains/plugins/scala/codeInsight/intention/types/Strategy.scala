package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

/**
 * Pavel.Fatin, 28.04.2010
 */

trait Strategy {
  def functionWithoutType(function: ScFunctionDefinition): Unit = {}

  def functionWithType(function: ScFunctionDefinition,
                       typeElement: ScTypeElement): Unit

  def valueWithoutType(value: ScPatternDefinition): Unit = {}

  def valueWithType(value: ScPatternDefinition,
                    typeElement: ScTypeElement): Unit

  def variableWithoutType(variable: ScVariableDefinition): Unit = {}

  def variableWithType(variable: ScVariableDefinition,
                       typeElement: ScTypeElement): Unit

  def patternWithoutType(pattern: ScBindingPattern): Unit = {}

  def wildcardPatternWithoutType(pattern: ScWildcardPattern): Unit = {}

  def patternWithType(pattern: ScTypedPattern): Unit = {}

  def parameterWithoutType(param: ScParameter): Unit = {}

  def parameterWithType(param: ScParameter): Unit = {}
}
