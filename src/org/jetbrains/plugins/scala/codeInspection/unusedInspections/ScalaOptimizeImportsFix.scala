package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections


import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.lang.String
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */

class ScalaOptimizeImportsFix extends IntentionAction {
  def getText: String = QuickFixBundle.message("optimize.imports.fix")

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    file.getManager.isInProject(file) && (file.isInstanceOf[ScalaFile] || ScalaLanguageDerivative.hasDerivativeOnFile(file))
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return
    new ScalaImportOptimizer().processFile(file).run()
  }

  def getFamilyName: String = QuickFixBundle.message("optimize.imports.fix")
}

class ScalaEnableOptimizeImportsOnTheFlyFix extends IntentionAction {
  def getText: String = QuickFixBundle.message("enable.optimize.imports.on.the.fly")

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    !ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true
    if (file.getManager.isInProject(file) && (file.isInstanceOf[ScalaFile] || ScalaLanguageDerivative.hasDerivativeOnFile(file))) {
      if (!FileModificationService.getInstance.prepareFileForWrite(file)) return
      new ScalaImportOptimizer().processFile(file).run()
    }
  }

  def getFamilyName: String = QuickFixBundle.message("enable.optimize.imports.on.the.fly")
}