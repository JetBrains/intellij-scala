package org.jetbrains.plugins.scala
package codeInsight.intention.recursion

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
 * Pavel Fatin
 */

class AddTailRecursionAnnotationIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Recursion"

  override def getText = "Add @tailrec annotation"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = element match {
    case it @ Parent(f: ScFunctionDefinition) if f.nameId == it =>
      !f.hasTailRecursionAnnotation && f.recursionType == RecursionType.TailRecursion
    case _ => false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val f = element.getParent.asInstanceOf[ScFunctionDefinition]
    f.addAnnotation("scala.annotation.tailrec")
  }
}