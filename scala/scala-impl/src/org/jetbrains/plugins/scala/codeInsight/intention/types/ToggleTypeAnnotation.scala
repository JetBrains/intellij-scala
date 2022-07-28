package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class ToggleTypeAnnotation extends AbstractTypeAnnotationIntention {

  import ToggleTypeAnnotation._

  override def getFamilyName: String = FamilyName

  override protected def descriptionStrategy: Strategy = new Strategy {

    override def functionWithoutType(function: ScFunctionDefinition): Boolean = {
      setText(message("intention.type.annotation.function.add.text"))

      true
    }

    override def functionWithType(function: ScFunctionDefinition,
                                  typeElement: ScTypeElement): Boolean = {
      setText(message("intention.type.annotation.function.remove.text"))

      true
    }

    override def valueWithoutType(value: ScPatternDefinition): Boolean = {
      setText(message("intention.type.annotation.value.add.text"))

      true
    }

    override def valueWithType(value: ScPatternDefinition,
                               typeElement: ScTypeElement): Boolean = {
      setText(message("intention.type.annotation.value.remove.text"))

      true
    }

    override def variableWithoutType(variable: ScVariableDefinition): Boolean = {
      setText(message("intention.type.annotation.variable.add.text"))

      true
    }

    override def variableWithType(variable: ScVariableDefinition,
                                  typeElement: ScTypeElement): Boolean = {
      setText(message("intention.type.annotation.variable.remove.text"))

      true
    }

    override def patternWithoutType(pattern: ScBindingPattern): Boolean = {
      setText(message("intention.type.annotation.pattern.add.text"))

      true
    }

    override def wildcardPatternWithoutType(pattern: ScWildcardPattern): Boolean = {
      setText(message("intention.type.annotation.pattern.add.text"))

      true
    }

    override def patternWithType(pattern: ScTypedPattern): Boolean = {
      setText(message("intention.type.annotation.pattern.remove.text"))

      true
    }

    override def parameterWithoutType(param: ScParameter): Boolean = {
      setText(message("intention.type.annotation.parameter.add.text"))

      true
    }

    override def parameterWithType(param: ScParameter): Boolean = {
      setText(message("intention.type.annotation.parameter.remove.text"))

      true
    }

    override def underscoreSectionWithoutType(underscore: ScUnderscoreSection): Boolean ={
      setText(message("intention.type.annotation.underscore.add.text"))

      true
    }

    override def underscoreSectionWithType(underscore: ScUnderscoreSection): Boolean = {
      setText(message("intention.type.annotation.underscore.remove.text"))

      true
    }
  }

  override protected def invocationStrategy(maybeEditor: Option[Editor]): Strategy =
    new AddOrRemoveStrategy(maybeEditor)
}

object ToggleTypeAnnotation {
  private[types] val FamilyName: String =
    message("intention.type.annotation.toggle.family")
}




