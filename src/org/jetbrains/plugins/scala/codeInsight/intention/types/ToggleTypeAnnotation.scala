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
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel.Fatin, 22.04.2010
 */

class ToggleTypeAnnotation extends PsiElementBaseIntentionAction {
  def getFamilyName = ScalaBundle.message("intention.type.annotation.toggle.family")

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    if (element == null || !IntentionAvailabilityChecker.checkIntention(this, element)) {
      false
    } else {
      def message(key: String) {
        setText(ScalaBundle.message(key))
      }
      implicit val typeSystem = project.typeSystem
      ToggleTypeAnnotation.complete(new Description(message), element)
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
        strategy.removeFromFunction(function)
      else
        strategy.addToFunction(function)

      return true
    }

    for {value <- element.parentsInFile.findByType(classOf[ScPatternDefinition])
         if value.expr.forall(!_.isAncestorOf(element))
         if value.pList.allPatternsSimple
         bindings = value.bindings
         if bindings.size == 1
         binding <- bindings} {

      if (value.typeElement.isDefined)
        strategy.removeFromValue(value)
      else
        strategy.addToValue(value)

      return true
    }

    for {variable <- element.parentsInFile.findByType(classOf[ScVariableDefinition])
         if variable.expr.forall(!_.isAncestorOf(element))
         if variable.pList.allPatternsSimple
         bindings = variable.bindings
         if bindings.size == 1
         binding <- bindings} {

      if (variable.typeElement.isDefined)
        strategy.removeFromVariable(variable)
      else
        strategy.addToVariable(variable)

      return true
    }

    for {
      param <- element.parentsInFile.findByType(classOf[ScParameter])
    } {
      param.parentsInFile.findByType(classOf[ScFunctionExpr]) match {
        case Some(func) =>
          if (param.typeElement.isDefined) {
            strategy.removeFromParameter(param)
            return true
          } else {
            val index = func.parameters.indexOf(param)
            func.expectedType() match {
              case Some(ScFunctionType(_, params)) =>
                if (index >= 0 && index < params.length) {
                  strategy.addToParameter(param)
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
          strategy.removeFromPattern(p)
          return true
        case _: ScReferencePattern =>
          strategy.addToPattern(pattern)
          return true
        case _ =>
      }
    }
    for (pattern <- element.parentsInFile.findByType(classOf[ScWildcardPattern])) {
      strategy.addToWildcardPattern(pattern)
      return true
    }

    false
  }
}




