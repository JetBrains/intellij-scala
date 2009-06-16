package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import lang.psi.api.toplevel.typedef.{ScTemplateDefinition}
import overrideImplement.ScalaOIUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */

class ImplementMethodsQuickFix(clazz: ScTemplateDefinition) extends IntentionAction {
  def getText: String = ScalaBundle.message("implement.methods.fix")
  def startInWriteAction: Boolean = false
  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = clazz.isValid && clazz.getManager.isInProject(file)
  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = ScalaOIUtil.invokeOverrideImplement(project, editor, file, true)
  def getFamilyName: String = ScalaBundle.message("implement.methods.fix")
}