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

/**
  * Markus.Hauck, 18.05.2016
  */

class RegenerateTypeAnnotation extends PsiElementBaseIntentionAction {

  def getFamilyName: String = RegenerateTypeAnnotation.getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (element == null || !IntentionAvailabilityChecker.checkIntention(this, element)) {
      false
    } else {
      def message(key: String) {
        setText(ScalaBundle.message(key))
      }
      implicit val typeSystem = project.typeSystem

      getTypeAnnotation(element).isDefined &&
        ToggleTypeAnnotation.complete(new RegenerateTypeAnnotationDescription(message), element)
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    ToggleTypeAnnotation.complete(new RegenerateStrategy(Option(editor)), element)(project.typeSystem)
  }

  private def getTypeAnnotation(element: PsiElement)
    (implicit typeSystem: TypeSystem): Option[ScTypeElement] = {
    def funType: Option[ScTypeElement] = for {
      function <- element.parentsInFile.findByType[ScFunctionDefinition]
      if function.hasAssign
      body <- function.body
      if !body.isAncestorOf(element)
      r <- function.returnTypeElement
    } yield r

    def valType: Option[ScTypeElement] =
      element.parentsInFile.findByType[ScPatternDefinition].flatMap(_.typeElement)

    def varType: Option[ScTypeElement] =
      element.parentsInFile.findByType[ScVariableDefinition].flatMap(_.typeElement)

    funType orElse valType orElse varType
  }
}

object RegenerateTypeAnnotation {
  def getFamilyName: String = ScalaBundle.message("intention.type.annotation.regen.family")
}
