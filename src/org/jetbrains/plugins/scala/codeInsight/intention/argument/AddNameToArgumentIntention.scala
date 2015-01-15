package org.jetbrains.plugins.scala
package codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.util.IntentionUtils

/**
 * Jason Zaugg
 */

object AddNameToArgumentIntention {
  def familyName = "Use named arguments"
}

class AddNameToArgumentIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = AddNameToArgumentIntention.familyName

  override def getText = "Use named arguments for current and subsequent arguments"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
     IntentionUtils.addNameToArgumentsFix(element, onlyBoolean = false).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!element.isValid) return
    IntentionUtils.addNameToArgumentsFix(element, onlyBoolean = false) match {
      case Some(x) => x()
      case None =>
    }
  }
}
