package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeSystem}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel.Fatin, 22.04.2010
 */

class ToggleTypeAnnotation extends PsiElementBaseIntentionAction {
  def getFamilyName: String = ScalaBundle.message("intention.type.annotation.toggle.family")

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (element == null || !IntentionAvailabilityChecker.checkIntention(this, element)) {
      false
    } else {
      def message(key: String) {
        setText(ScalaBundle.message(key))
      }
      implicit val typeSystem = project.typeSystem
      ToggleTypeAnnotation.complete(new ToggleTypeAnnotationDescription(message), element)
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    implicit val typeSystem = project.typeSystem
    ToggleTypeAnnotation.complete(new AddOrRemoveStrategy(Option(editor)), element)
  }
}

object ToggleTypeAnnotation {
  def complete(strategy: Strategy, element: PsiElement)
              (implicit typeSystem: TypeSystem): Boolean = {
    for {function <- element.parentsInFile.findByType(classOf[ScFunctionDefinition])
         if function.hasAssign
         body <- function.body
         if !body.isAncestorOf(element)} {

      if (function.returnTypeElement.isDefined)
        strategy.functionWithType(function)
      else
        strategy.functionWithoutType(function)

      return true
    }

    for {value <- element.parentsInFile.findByType(classOf[ScPatternDefinition])
         if value.expr.forall(!_.isAncestorOf(element))
         if value.pList.simplePatterns
         bindings = value.bindings
         if bindings.size == 1
         binding <- bindings} {

      if (value.typeElement.isDefined)
        strategy.valueWithType(value)
      else
        strategy.valueWithoutType(value)

      return true
    }

    for {variable <- element.parentsInFile.findByType(classOf[ScVariableDefinition])
         if variable.expr.forall(!_.isAncestorOf(element))
         if variable.pList.simplePatterns
         bindings = variable.bindings
         if bindings.size == 1
         binding <- bindings} {

      if (variable.typeElement.isDefined)
        strategy.variableWithType(variable)
      else
        strategy.variableWithoutType(variable)

      return true
    }

    for {
      param <- element.parentsInFile.findByType(classOf[ScParameter])
    } {
      param.parentsInFile.findByType(classOf[ScFunctionExpr]) match {
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

    for (pattern <- element.parentsInFile.findByType(classOf[ScBindingPattern])) {
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
    for (pattern <- element.parentsInFile.findByType(classOf[ScWildcardPattern])) {
      strategy.wildcardPatternWithoutType(pattern)
      return true
    }

    false
  }
}




