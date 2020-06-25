package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle

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

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.use.named.arguments")

  override def getText: String = ScalaCodeInsightBundle.message("use.named.arguments.for.current.and.subsequent.arguments")
}
