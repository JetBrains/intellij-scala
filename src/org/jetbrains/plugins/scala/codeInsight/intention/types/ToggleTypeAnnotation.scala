package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi.{PsiFile, PsiElement}
import lang.psi.api.base.patterns._

/**
 * Pavel.Fatin, 22.04.2010
 */

class ToggleTypeAnnotation extends PsiElementBaseIntentionAction {
  def getFamilyName = ScalaBundle.message("intention.type.annotation.toggle.family")

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    def message(key: String) = setText(ScalaBundle.message(key))
    complete(new Description(message), element)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    file.elementAt(editor.getCaretModel.getOffset).foreach(complete(Update, _))
  }

  def complete(strategy: Strategy, element: PsiElement): Boolean = {
    for{function <- element.parentOfType(classOf[ScFunctionDefinition])
        if function.hasAssign
        body <- function.body
        if (!body.isAncestorOf(element))} {

      if (function.returnTypeElement.isDefined)
        strategy.removeFromFunction(function)
      else
        strategy.addToFunction(function)

      return true
    }

    for{value <- element.parentOfType(classOf[ScPatternDefinition])
        if (value.expr.toOption.map(!_.isAncestorOf(element)).getOrElse(true))
        if (value.pList.allPatternsSimple)
        bindings = value.bindings
        if (bindings.size == 1)
        binding <- bindings} {

      if (value.typeElement.isDefined)
        strategy.removeFromValue(value)
      else
        strategy.addToValue(value)

      return true
    }

    for{variable <- element.parentOfType(classOf[ScVariableDefinition])
        if (variable.expr.toOption.map(!_.isAncestorOf(element)).getOrElse(true))
        if (variable.pList.allPatternsSimple)
        bindings = variable.bindings
        if (bindings.size == 1)
        binding <- bindings} {

      if (variable.typeElement.isDefined)
        strategy.removeFromVariable(variable)
      else
        strategy.addToVariable(variable)

      return true
    }

    for (pattern <- element.parentOfType(classOf[ScBindingPattern])) {

      pattern match {
        case p: ScTypedPattern if (p.typePattern.isDefined) =>
          strategy.removeFromPattern(p)
        case _ => strategy.addToPattern(pattern)
      }

      return true
    }

    false
  }
}




