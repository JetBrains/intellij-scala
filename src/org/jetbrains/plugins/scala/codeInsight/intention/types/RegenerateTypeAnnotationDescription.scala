package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

/**
  * Markus.Hauck, 18.05.2016
  */

class RegenerateTypeAnnotationDescription(message: String => Unit) extends StrategyAdapter {
  override def functionWithType(function: ScFunctionDefinition): Unit =
    message("intention.type.annotation.function.regenerate.text")

  override def variableWithType(variable: ScVariableDefinition): Unit =
    message("intention.type.annotation.variable.regenerate.text")

  override def valueWithType(value: ScPatternDefinition): Unit =
    message("intention.type.annotation.value.regenerate.text")
}
