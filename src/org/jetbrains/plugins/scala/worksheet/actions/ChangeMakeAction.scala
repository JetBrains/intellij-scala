package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, AnAction}
import com.intellij.openapi.util.IconLoader
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project

/**
 * User: Dmitry.Naydanov
 * Date: 07.04.14.
 */
class ChangeMakeAction(virtualFile: VirtualFile, project: Project) extends AnAction with TopComponentAction {
  private def getPsi = PsiManager getInstance project findFile virtualFile

  override def actionPerformed(e: AnActionEvent) {
    val psi = getPsi
    if (psi == null || !psi.isValid) return

    val now = !WorksheetCompiler.isMakeBeforeRun(psi)
    WorksheetCompiler.setMakeBeforeRun(psi, now)
    getTemplatePresentation.setIcon(
      if (now) ChangeMakeAction.WORKSHEET_COMPILE_ICON else ChangeMakeAction.WORKSHEET_NO_COMPILE_ICON
    )
  }

  override def actionIcon =
    if (WorksheetCompiler isMakeBeforeRun getPsi) ChangeMakeAction.WORKSHEET_COMPILE_ICON
    else ChangeMakeAction.WORKSHEET_NO_COMPILE_ICON

  override def bundleKey: String = "worksheet.change.make.button"
}

object ChangeMakeAction {
  private val WORKSHEET_NO_COMPILE_ICON = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/no_compile.png")
  private val WORKSHEET_COMPILE_ICON = AllIcons.Actions.Compile
}
