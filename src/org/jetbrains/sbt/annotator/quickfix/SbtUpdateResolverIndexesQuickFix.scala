package org.jetbrains.sbt
package annotator.quickfix

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.psi.PsiFile

/**
 * @author Nikolay Obedin
 * @since 8/5/14.
 */
class SbtUpdateResolverIndexesQuickFix(module: Module) extends AbstractIntentionAction {

  def getText = SbtBundle("sbt.fix.updateIndexes")

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    val ui = ProjectStructureConfigurable.getInstance(project)
    val editor = new SingleConfigurableEditor(project, ui)
    ui.select(module.getName, "SBT", false)
    //Project Structure should be shown in a transaction
    TransactionGuard.getInstance().submitTransactionAndWait(new Runnable {
      def run(): Unit = editor.show()
    })
  }
}
