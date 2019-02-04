package org.jetbrains.plugins.scala
package codeInsight
package intention
package recursion

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
  * Pavel Fatin
  */
final class AddTailRecursionAnnotationIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element.getParent match {
      case function: ScFunctionDefinition if function.nameId == element =>
        !function.hasTailRecursionAnnotation && function.recursionType == RecursionType.TailRecursion
      case _ => false
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    element.getParent match {
      case function: ScFunctionDefinition => function.addAnnotation("scala.annotation.tailrec")
    }

  override def getFamilyName = "Recursion"

  override def getText = "Add @tailrec annotation"
}