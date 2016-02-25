package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

/**
 * Pavel.Fatin, 28.04.2010
 */

class Description(message: String => Unit) extends Strategy {
  def addToFunction(function: ScFunctionDefinition) {
    message("intention.type.annotation.function.add.text")
  }

  def removeFromFunction(function: ScFunctionDefinition) {
    message("intention.type.annotation.function.remove.text")
  }

  def addToValue(value: ScPatternDefinition) {
    message("intention.type.annotation.value.add.text")
  }

  def removeFromValue(value: ScPatternDefinition) {
    message("intention.type.annotation.value.remove.text")
  }

  def addToVariable(variable: ScVariableDefinition) {
    message("intention.type.annotation.variable.add.text")
  }

  def removeFromVariable(variable: ScVariableDefinition) {
    message("intention.type.annotation.variable.remove.text")
  }

  def addToPattern(pattern: ScBindingPattern) {
    message("intention.type.annotation.pattern.add.text")
  }

  def addToWildcardPattern(pattern: ScWildcardPattern) {
    message("intention.type.annotation.pattern.add.text")
  }

  def removeFromPattern(pattern: ScTypedPattern) {
    message("intention.type.annotation.pattern.remove.text")
  }

  def addToParameter(param: ScParameter) {
    message("intention.type.annotation.parameter.add.text")
  }

  def removeFromParameter(param: ScParameter) {
    message("intention.type.annotation.parameter.remove.text")
  }
}
