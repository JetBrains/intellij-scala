package org.jetbrains.plugins.scala
package annotator
package quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */

class ImplementMethodsQuickFix(clazz: ScTemplateDefinition) extends IntentionAction {
  def getText: String = ScalaBundle.message("implement.methods.fix")
  def startInWriteAction: Boolean = false
  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    clazz.isValid && clazz.getManager.isInProject(file) && file.isWritable
  def invoke(project: Project, editor: Editor, file: PsiFile) {
    ScalaOIUtil.invokeOverrideImplement(project, editor, file, isImplement = true)
  }
  def getFamilyName: String = ScalaBundle.message("implement.methods.fix")
}