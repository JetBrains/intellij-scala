package org.jetbrains.plugins.scala
package util.macroDebug

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Dmitry Naydanov
 * Date: 11/5/12
 */
class OpenSynFileIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Scala"

  def invoke(project: Project, editor: Editor, element: PsiElement) {
    val file = ScalaMacroDebuggingUtil loadCode element.getContainingFile
    if (file != null) file navigate true
  }

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = element.getContainingFile match {
    case scalaFile: ScalaFile if (ScalaMacroDebuggingUtil.isEnabled) && (ScalaMacroDebuggingUtil isLoaded scalaFile) =>  true
    case _ => false
  }

  override def getText: String = "Navigate to file with macros expanded"

  override def setText(text: String) { }
}
