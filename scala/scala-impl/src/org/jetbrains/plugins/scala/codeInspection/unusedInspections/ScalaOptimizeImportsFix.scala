package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.{HighPriorityAction, IntentionAction, LowPriorityAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.FileContentUtil
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer.findOptimizerFor
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.jdk.CollectionConverters._

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */
sealed abstract class ScalaOptimizeImportsFixBase extends IntentionAction {

  override final def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    file.getManager.isInProject(file) && file.hasScalaPsi

  /**
   * We can't just select ScalaImportOptimizer because of Play2 templates
   *
   * @param file Any parallel psi file
   */
  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = for {
    scalaFile <- file.findAnyScalaFile
    if FileModificationService.getInstance.prepareFileForWrite(scalaFile)

    optimizer <- findOptimizerFor(scalaFile)
    runner = optimizer.processFile(scalaFile)
  } runner.run()

  override final def getFamilyName: String = getText
}

final class ScalaOptimizeImportsFix extends ScalaOptimizeImportsFixBase with HighPriorityAction {

  override def getText: String = QuickFixBundle.message("optimize.imports.fix")
}

final class ScalaEnableOptimizeImportsOnTheFlyFix extends ScalaOptimizeImportsFixBase {

  override def getText: String = QuickFixBundle.message("enable.optimize.imports.on.the.fly")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    !ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY &&
      super.isAvailable(project, editor, file)

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    ScalaApplicationSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true
    super.invoke(project, editor, file)
  }
}

final class MarkImportAsAlwaysUsed(importText: String) extends IntentionAction with LowPriorityAction {

  override def getText: String = ScalaInspectionBundle.message("mark.import.as.always.used.in.this.project")

  override def startInWriteAction: Boolean = true

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    importText.contains(".") && !ScalaCodeStyleSettings.getInstance(project).isAlwaysUsedImport(importText)
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(project)
    settings.setAlwaysUsedImports((settings.getAlwaysUsedImports ++ Array(importText)).sorted)
    FileContentUtil.reparseFiles(project, Seq(file.getVirtualFile).asJava, true)
  }

  override def getFamilyName: String = ScalaInspectionBundle.message("mark.import.as.always.used.in.this.project")
}