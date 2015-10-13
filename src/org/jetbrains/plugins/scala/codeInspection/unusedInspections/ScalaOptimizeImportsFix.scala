package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections


import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.{HighPriorityAction, IntentionAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */

class ScalaOptimizeImportsFix extends IntentionAction with HighPriorityAction {
  def getText: String = QuickFixBundle.message("optimize.imports.fix")

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    file.getManager.isInProject(file) && (file.isInstanceOf[ScalaFile] || ScalaLanguageDerivative.hasDerivativeOnFile(file))
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return

    file match {
      case scalaPsi: ScalaFile => ScalaImportOptimizer.runOptimizerUnsafe(scalaPsi)
      case _ =>
    }
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

      file match {
        case scalaFile: ScalaFile => ScalaImportOptimizer.runOptimizerUnsafe(scalaFile)
        case _ =>
      }
    }
  }

  def getFamilyName: String = QuickFixBundle.message("enable.optimize.imports.on.the.fly")
}