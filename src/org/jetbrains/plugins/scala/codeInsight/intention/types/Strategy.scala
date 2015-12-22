package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

/**
 * Pavel.Fatin, 28.04.2010
 */

trait Strategy {
  def addToFunction(function: ScFunctionDefinition, editor: Option[Editor])

  def removeFromFunction(function: ScFunctionDefinition)

  def addToValue(value: ScPatternDefinition, editor: Option[Editor])

  def removeFromValue(value: ScPatternDefinition)

  def addToVariable(variable: ScVariableDefinition, editor: Option[Editor])

  def removeFromVariable(variable: ScVariableDefinition)

  def addToPattern(pattern: ScBindingPattern, editor: Option[Editor])

  def addToWildcardPattern(pattern: ScWildcardPattern)

  def removeFromPattern(pattern: ScTypedPattern)

  def addToParameter(param: ScParameter, editor: Option[Editor])

  def removeFromParameter(param: ScParameter)
}