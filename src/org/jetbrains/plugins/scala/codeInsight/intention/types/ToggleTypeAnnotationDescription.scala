package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

/**
 * Pavel.Fatin, 28.04.2010
 */

class ToggleTypeAnnotationDescription(message: String => Unit) extends Strategy {
  def functionWithoutType(function: ScFunctionDefinition) {
    message("intention.type.annotation.function.add.text")
  }

  def functionWithType(function: ScFunctionDefinition) {
    message("intention.type.annotation.function.remove.text")
  }

  def valueWithoutType(value: ScPatternDefinition) {
    message("intention.type.annotation.value.add.text")
  }

  def valueWithType(value: ScPatternDefinition) {
    message("intention.type.annotation.value.remove.text")
  }

  def variableWithoutType(variable: ScVariableDefinition) {
    message("intention.type.annotation.variable.add.text")
  }

  def variableWithType(variable: ScVariableDefinition) {
    message("intention.type.annotation.variable.remove.text")
  }

  def patternWithoutType(pattern: ScBindingPattern) {
    message("intention.type.annotation.pattern.add.text")
  }

  def wildcardPatternWithoutType(pattern: ScWildcardPattern) {
    message("intention.type.annotation.pattern.add.text")
  }

  def patternWithType(pattern: ScTypedPattern) {
    message("intention.type.annotation.pattern.remove.text")
  }

  def parameterWithoutType(param: ScParameter) {
    message("intention.type.annotation.parameter.add.text")
  }

  def parameterWithType(param: ScParameter) {
    message("intention.type.annotation.parameter.remove.text")
  }
}
