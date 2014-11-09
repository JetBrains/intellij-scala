package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * Nikolay.Tropin
 * 2014-07-28
 */
abstract class CreateFromUsageQuickFixBase(ref: ScReferenceElement, description: String) extends IntentionAction {
  
  val getText = s"Create $description '${ref.nameId.getText}'"

  val getFamilyName = s"Create $description"

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!ref.isValid) return false
    if (!ref.getManager.isInProject(file)) return false
    if (!file.isInstanceOf[ScalaFile]) return false
    if (file.isInstanceOf[ScalaCodeFragment]) return false
    
    true
  }

  override def startInWriteAction() = false

  override def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!ref.isValid) return

    invokeInner(project, editor, file)
  }

  protected def invokeInner(project: Project, editor: Editor, file: PsiFile)
}