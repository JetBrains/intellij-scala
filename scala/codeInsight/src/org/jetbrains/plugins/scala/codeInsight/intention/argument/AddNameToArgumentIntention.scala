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
final class AddNameToArgumentIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    addNameToArgumentsFix(element, onlyBoolean = false).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (!element.isValid) return
    addNameToArgumentsFix(element, onlyBoolean = false).foreach(_.apply())
  }

  override def getFamilyName: String = AddNameToArgumentIntention.FamilyName

  override def getText = "Use named arguments for current and subsequent arguments"
}

object AddNameToArgumentIntention {

  private[argument] val FamilyName = "Use named arguments"
}
