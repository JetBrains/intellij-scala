package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.openapi.actionSystem.{AnAction, DataContext, PlatformCoreDataKeys}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.actions.SingleActionPromoterBase

class RunWorksheetActionPromoter extends SingleActionPromoterBase {

  override def shouldPromote(anAction: AnAction, context: DataContext): Boolean =
    anAction.isInstanceOf[RunWorksheetAction] && isInInScalaFile(context)

  private def isInInScalaFile(context: DataContext): Boolean = {
    //According to Greg Shrago context.getData(CommonDataKeys.VIRTUAL_FILE) might be not reliable approach
    //and better to use file editor instead
    val fileEditor = context.getData(PlatformCoreDataKeys.FILE_EDITOR)
    fileEditor != null && {
      val vFile = fileEditor.getFile
      vFile != null && hasScalaWorksheetExtension(vFile)
    }
  }

  private def hasScalaWorksheetExtension(vFile: VirtualFile) = {
    val extension = vFile.getExtension
    extension == "sc" ||
      extension == "scala" // for Scala Scratch files which behave like extensions
  }
}