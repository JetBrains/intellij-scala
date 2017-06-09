package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
  * Markus.Hauck, 18.05.2016
  */
class RegenerateTypeAnnotation extends AbstractTypeAnnotationIntention {

  import RegenerateTypeAnnotation._

  override def getFamilyName: String = FamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    getTypeAnnotation(element) &&
      super.isAvailable(project, editor, element)

  override protected def descriptionStrategy: Strategy = new Strategy {

    override def functionWithType(function: ScFunctionDefinition,
                                  typeElement: ScTypeElement): Unit =
      setText(message("intention.type.annotation.function.regenerate.text"))

    override def variableWithType(variable: ScVariableDefinition,
                                  typeElement: ScTypeElement): Unit =
      setText(message("intention.type.annotation.variable.regenerate.text"))

    override def valueWithType(value: ScPatternDefinition,
                               typeElement: ScTypeElement): Unit =
      setText(message("intention.type.annotation.value.regenerate.text"))
  }


  override protected def invocationStrategy(maybeEditor: Option[Editor]): Strategy = new Strategy {
    private val strategy = new AddOrRemoveStrategy(maybeEditor)

    override def functionWithType(function: ScFunctionDefinition,
                                  typeElement: ScTypeElement): Unit = {
      strategy.functionWithType(function, typeElement)
      strategy.functionWithoutType(function)
    }

    override def variableWithType(variable: ScVariableDefinition,
                                  typeElement: ScTypeElement): Unit = {
      strategy.variableWithType(variable, typeElement)
      strategy.variableWithoutType(variable)
    }

    override def valueWithType(value: ScPatternDefinition,
                               typeElement: ScTypeElement): Unit = {
      strategy.valueWithType(value, typeElement)
      strategy.valueWithoutType(value)
    }
  }
}

object RegenerateTypeAnnotation {
  private[types] val FamilyName: String =
    message("intention.type.annotation.regen.family")

  private def getTypeAnnotation(element: PsiElement): Boolean = {
    import AbstractTypeAnnotationIntention._

    def parents = functionParent(element).flatMap(_.returnTypeElement) ++
      (valueParent(element) ++ variableParent(element)).flatMap(_.typeElement)

    element != null && parents.nonEmpty
  }
}
