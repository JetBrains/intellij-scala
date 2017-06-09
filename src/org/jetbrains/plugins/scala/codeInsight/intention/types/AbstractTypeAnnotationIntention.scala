package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkIntention

abstract class AbstractTypeAnnotationIntention extends PsiElementBaseIntentionAction {

  import AbstractTypeAnnotationIntention.complete

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element match {
      case _: PsiElement if checkIntention(this, element) =>
        complete(element, descriptionStrategy)
      case _ => false
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    complete(element, invocationStrategy(Option(editor)))

  protected def descriptionStrategy: Strategy

  protected def invocationStrategy(maybeEditor: Option[Editor]): Strategy
}

object AbstractTypeAnnotationIntention {

  private[types] def functionParent(element: PsiElement): Option[ScFunctionDefinition] =
    for {
      function <- element.parentsInFile.findByType[ScFunctionDefinition]
      if function.hasAssign
      body <- function.body
      if !body.isAncestorOf(element)
    } yield function

  private[types] def valueParent(element: PsiElement): Option[ScPatternDefinition] =
    for {
      value <- element.parentsInFile.findByType[ScPatternDefinition]
      if value.expr.forall(!_.isAncestorOf(element))
      if value.pList.simplePatterns
      if value.bindings.size == 1
    } yield value

  private[types] def variableParent(element: PsiElement): Option[ScVariableDefinition] =
    for {
      variable <- element.parentsInFile.findByType[ScVariableDefinition]
      if variable.expr.forall(!_.isAncestorOf(element))
      if variable.pList.simplePatterns
      if variable.bindings.size == 1
    } yield variable

  def complete(element: PsiElement,
               strategy: Strategy = new AddOnlyStrategy): Boolean = {
    functionParent(element).foreach { function =>
      function.returnTypeElement match {
        case Some(typeElement) =>
          strategy.functionWithType(function, typeElement)
        case _ =>
          strategy.functionWithoutType(function)
      }

      return true
    }

    valueParent(element).foreach { value =>
      value.typeElement match {
        case Some(typeElement) =>
          strategy.valueWithType(value, typeElement)
        case _ =>
          strategy.valueWithoutType(value)
      }

      return true
    }

    variableParent(element).foreach { variable =>
      variable.typeElement match {
        case Some(typeElement) =>
          strategy.variableWithType(variable, typeElement)
        case _ =>
          strategy.variableWithoutType(variable)
      }

      return true
    }

    for {
      param <- element.parentsInFile.findByType[ScParameter]
    } {
      param.parentsInFile.findByType[ScFunctionExpr] match {
        case Some(func) =>
          if (param.typeElement.isDefined) {
            strategy.parameterWithType(param)
            return true
          } else {
            val index = func.parameters.indexOf(param)
            func.expectedType() match {
              case Some(FunctionType(_, params)) =>
                if (index >= 0 && index < params.length) {
                  strategy.parameterWithoutType(param)
                  return true
                }
              case _ =>
            }
          }
        case _ =>
      }
    }

    for (pattern <- element.parentsInFile.findByType[ScBindingPattern]) {
      pattern match {
        case p: ScTypedPattern if p.typePattern.isDefined =>
          strategy.patternWithType(p)
          return true
        case _: ScReferencePattern =>
          strategy.patternWithoutType(pattern)
          return true
        case _ =>
      }
    }
    for (pattern <- element.parentsInFile.findByType[ScWildcardPattern]) {
      strategy.wildcardPatternWithoutType(pattern)
      return true
    }

    false
  }
}
