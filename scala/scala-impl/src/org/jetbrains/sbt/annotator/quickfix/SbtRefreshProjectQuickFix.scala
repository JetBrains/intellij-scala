package org.jetbrains.sbt
package annotator.quickfix

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.sbt.project.SbtProjectSystem

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */
class SbtRefreshProjectQuickFix extends AbstractIntentionAction {

  override def getText: String = SbtBundle.message("sbt.fix.refreshProject")

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }
}
