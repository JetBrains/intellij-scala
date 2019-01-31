package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Jason Zaugg
 */

object AddNameToArgumentIntention {
  def familyName = "Use named arguments"
}

class AddNameToArgumentIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = AddNameToArgumentIntention.familyName

  override def getText = "Use named arguments for current and subsequent arguments"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    addNameToArgumentsFix(element, onlyBoolean = false).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!element.isValid) return
    addNameToArgumentsFix(element, onlyBoolean = false).foreach(_.apply())
  }
}
