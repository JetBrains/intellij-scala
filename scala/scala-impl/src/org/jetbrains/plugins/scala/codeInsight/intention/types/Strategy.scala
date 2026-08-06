package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern, ScTypedPatternLike, ScWildcardPattern}
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

  /**
   * This is supposed to work only for [[ScBindingPattern]] and [[ScWildcardPattern]]
   */
  def patternWithoutType(pattern: ScPattern) = true

  def patternWithType(pattern: ScTypedPatternLike): Boolean = true

  def parameterWithoutType(param: ScParameter): Boolean = true

  def parameterWithType(param: ScParameter): Boolean = true

  def underscoreSectionWithoutType(underscore: ScUnderscoreSection): Boolean = true

  def underscoreSectionWithType(underscore: ScUnderscoreSection): Boolean = true
}
