package org.jetbrains.plugins.scala
package codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
 * Jason Zaugg
 */

class AddNameToArgumentIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Use named arguments"

  override def getText = "Use named arguments for current and subsequent arguments"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
     IntentionUtils.check(project, element).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!element.isValid) return
    IntentionUtils.check(project, element) match {
      case Some(x) => x()
      case None =>
    }
  }
}
