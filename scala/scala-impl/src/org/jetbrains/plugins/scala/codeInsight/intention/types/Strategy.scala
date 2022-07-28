package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

trait Strategy {
  def functionWithoutType(function: ScFunctionDefinition): Boolean = true

  def functionWithType(function: ScFunctionDefinition,
                       typeElement: ScTypeElement): Boolean

  def valueWithoutType(value: ScPatternDefinition): Boolean = true

  def valueWithType(value: ScPatternDefinition,
                    typeElement: ScTypeElement): Boolean

  def variableWithoutType(variable: ScVariableDefinition): Boolean = true

  def variableWithType(variable: ScVariableDefinition,
                       typeElement: ScTypeElement): Boolean

  def patternWithoutType(pattern: ScBindingPattern): Boolean = true

  def wildcardPatternWithoutType(pattern: ScWildcardPattern): Boolean = true

  def patternWithType(pattern: ScTypedPattern): Boolean = true

  def parameterWithoutType(param: ScParameter): Boolean = true

  def parameterWithType(param: ScParameter): Boolean = true

  def underscoreSectionWithoutType(underscore: ScUnderscoreSection): Boolean = true

  def underscoreSectionWithType(underscore: ScUnderscoreSection): Boolean = true
}
