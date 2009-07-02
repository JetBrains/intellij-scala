package org.jetbrains.plugins.scala.codeInspection.unusedInspections


import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import editor.importOptimizer.ScalaImportOptimizer
import java.lang.String
import lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */

class ScalaOptimizeImportsFix extends IntentionAction {
  def getText: String = QuickFixBundle.message("optimize.imports.fix")

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    return file.getManager().isInProject(file) && file.isInstanceOf[ScalaFile]
  }

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return
    new ScalaImportOptimizer().processFile(file).run
  }

  def getFamilyName: String = QuickFixBundle.message("optimize.imports.fix")
}