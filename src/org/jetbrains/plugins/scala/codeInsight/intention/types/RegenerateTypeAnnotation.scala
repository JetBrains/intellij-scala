package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

class RegenerateTypeAnnotation extends PsiElementBaseIntentionAction {
  import RegenerateTypeAnnotation._

  def getFamilyName = RegenerateTypeAnnotation.getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    if (element == null || !IntentionAvailabilityChecker.checkIntention(this, element)) {
      false
    } else {
      def message(key: String) {
        setText(ScalaBundle.message(key))
      }
      implicit val typeSystem = project.typeSystem

      getTypeAnnotation(element).isDefined &&
        complete(new Description(message), element)
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    complete(new AddOrRemoveStrategy(Option(editor)), element)(project.typeSystem)
  }
}

object RegenerateTypeAnnotation {
  def getFamilyName = ScalaBundle.message("intention.type.annotation.regen.family")

  def complete(strategy: Strategy, element: PsiElement)(implicit typeSystem: TypeSystem): Boolean = {
    for {function <- element.parentsInFile.findByType(classOf[ScFunctionDefinition])
         if function.hasAssign
         body <- function.body
         if !body.isAncestorOf(element)} {

      strategy.redoFromFunction(function)

      return true
    }

    for {value <- element.parentsInFile.findByType(classOf[ScPatternDefinition])
         if value.expr.forall(!_.isAncestorOf(element))
         if value.pList.allPatternsSimple
         bindings = value.bindings
         if bindings.size == 1
         binding <- bindings} {

      strategy.redoFromValue(value)

      return true
    }

    for {variable <- element.parentsInFile.findByType(classOf[ScVariableDefinition])
         if variable.expr.forall(!_.isAncestorOf(element))
         if variable.pList.allPatternsSimple
         bindings = variable.bindings
         if bindings.size == 1
         binding <- bindings} {

      strategy.redoFromVariable(variable)

      return true
    }

    false
  }

  private def getTypeAnnotation(element: PsiElement)
                               (implicit typeSystem: TypeSystem): Option[ScTypeElement] = {
    def funType: Option[ScTypeElement] = for {
      function <- element.parentsInFile.findByType(classOf[ScFunctionDefinition])
      if function.hasAssign
      body <- function.body
      if !body.isAncestorOf(element)
      r <- function.returnTypeElement
    } yield r

    def valType: Option[ScTypeElement] =
      element.parentsInFile.findByType(classOf[ScPatternDefinition]).flatMap(_.typeElement)

    def varType: Option[ScTypeElement] =
      element.parentsInFile.findByType(classOf[ScVariableDefinition]).flatMap(_.typeElement)

    funType orElse valType orElse varType
  }
}
