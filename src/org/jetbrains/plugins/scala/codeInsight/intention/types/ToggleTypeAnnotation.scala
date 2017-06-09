package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
  * Pavel.Fatin, 22.04.2010
  */
class ToggleTypeAnnotation extends AbstractTypeAnnotationIntention {

  import ToggleTypeAnnotation._

  override def getFamilyName: String = FamilyName

  override protected def descriptionStrategy: Strategy = new Strategy {

    override def functionWithoutType(function: ScFunctionDefinition): Unit =
      setText(message("intention.type.annotation.function.add.text"))

    def functionWithType(function: ScFunctionDefinition,
                         typeElement: ScTypeElement): Unit =
      setText(message("intention.type.annotation.function.remove.text"))

    override def valueWithoutType(value: ScPatternDefinition): Unit =
      setText(message("intention.type.annotation.value.add.text"))

    def valueWithType(value: ScPatternDefinition,
                      typeElement: ScTypeElement): Unit =
      setText(message("intention.type.annotation.value.remove.text"))

    override def variableWithoutType(variable: ScVariableDefinition): Unit =
      setText(message("intention.type.annotation.variable.add.text"))

    def variableWithType(variable: ScVariableDefinition,
                         typeElement: ScTypeElement): Unit =
      setText(message("intention.type.annotation.variable.remove.text"))

    override def patternWithoutType(pattern: ScBindingPattern): Unit =
      setText(message("intention.type.annotation.pattern.add.text"))

    override def wildcardPatternWithoutType(pattern: ScWildcardPattern): Unit =
      setText(message("intention.type.annotation.pattern.add.text"))

    override def patternWithType(pattern: ScTypedPattern): Unit =
      setText(message("intention.type.annotation.pattern.remove.text"))

    override def parameterWithoutType(param: ScParameter): Unit =
      setText(message("intention.type.annotation.parameter.add.text"))

    override def parameterWithType(param: ScParameter): Unit =
      setText(message("intention.type.annotation.parameter.remove.text"))
  }

  override protected def invocationStrategy(maybeEditor: Option[Editor]): Strategy =
    new AddOrRemoveStrategy(maybeEditor)
}

object ToggleTypeAnnotation {
  private[types] val FamilyName: String =
    message("intention.type.annotation.toggle.family")
}




