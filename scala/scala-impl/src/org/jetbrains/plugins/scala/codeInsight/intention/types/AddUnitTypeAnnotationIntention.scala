package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
  * @author Alefas
  * @since 29/08/16
  */
class AddUnitTypeAnnotationIntention extends PsiElementBaseIntentionAction {
  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (!isAvailable(project, editor, element)) return

    import quickfix._
    implicit val context: ProjectContext = project

    for {
      definition <- element.parentsInFile.findByType[ScFunctionDefinition]
      if !definition.hasAssign
      body <- definition.body
      if !body.isAncestorOf(element)
    } {
      removeAssignment(definition)
      removeTypeElement(definition)
      addUnitTypeElement(definition)
    }
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (element == null || !IntentionAvailabilityChecker.checkIntention(this, element)) {
      false
    } else {
      for {
        function <- element.parentsInFile.findByType[ScFunctionDefinition]
        if !function.hasAssign
        body <- function.body
        if !body.isAncestorOf(element)
      } {
        setText(ScalaBundle.message("intention.type.annotation.function.add.text"))
        return true
      }
      false
    }
  }

  override def getFamilyName: String = AddUnitTypeAnnotationIntention.familyName
}

object AddUnitTypeAnnotationIntention {
  def familyName: String = ScalaBundle.message("intention.add.explicit.unit.type.annotation")
}
