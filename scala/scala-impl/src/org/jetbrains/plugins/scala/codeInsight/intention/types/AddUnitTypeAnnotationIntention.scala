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

class AddUnitTypeAnnotationIntention extends PsiElementBaseIntentionAction {

  import AddUnitTypeAnnotationIntention._

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (!isAvailable(project, editor, element)) return

    findDefinition(element).foreach { definition =>
      import quickfix._
      implicit val context: ProjectContext = project

      removeAssignment(definition)
      removeTypeElement(definition)
      addUnitTypeElement(definition)
    }
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = element match {
    case null => false
    case _ if IntentionAvailabilityChecker.checkIntention(this, element)
      && findDefinition(element).isDefined =>
      setText(ScalaBundle.message("intention.type.annotation.function.add.text"))
      true
    case _ => false
  }

  override def getFamilyName: String = FamilyName
}

object AddUnitTypeAnnotationIntention {

  private[types] val FamilyName = ScalaBundle.message("intention.add.explicit.unit.type.annotation")

  private def findDefinition(element: PsiElement) = for {
    definition <- element.parentsInFile.findByType[ScFunctionDefinition]
    if !definition.hasAssign
    body <- definition.body
    if !body.isAncestorOf(element)
  } yield definition
}
