package org.jetbrains.plugins.scala.project.notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.ui.{EditorNotificationPanel, EditorNotificationProvider, EditorNotifications}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}
import org.jetbrains.sbt.Sbt

import java.util.function
import javax.swing.JComponent

//noinspection ApiStatus,UnstableApiUsage
abstract class AbstractNotificationProvider(@Nls kitTitle: String,
                                            project: Project)
  extends EditorNotificationProvider {

  import AbstractNotificationProvider._


  {
    val notifications = EditorNotifications.getInstance(project)
    project.subscribeToModuleRootChanged() { _ =>
      notifications.updateAllNotifications()
    }
  }

  @Nls
  protected def panelText(kitTitle: String): String

  protected def hasDeveloperKit(file: VirtualFile): Boolean

  protected def setDeveloperKit(file: VirtualFile, panel: EditorNotificationPanel): Unit

  override def collectNotificationData(project: Project, virtualFile: VirtualFile): function.Function[_ >: FileEditor, _ <: JComponent] = {
    PsiManager.getInstance(project).findFile(virtualFile) match {
      case file: PsiFile if isSourceCode(file) && !hasDeveloperKit(virtualFile) =>
        createPanelProvider(setDeveloperKit(virtualFile, _))
      case _ =>
        EditorNotificationProvider.CONST_NULL
    }
  }

  private def createPanelProvider(
    action: EditorNotificationPanel => Unit
  ): function.Function[_ >: FileEditor, _ <: JComponent] = { _ =>
    val panel = new EditorNotificationPanel()
      .text(panelText(kitTitle))
    panel.createActionLabel(ScalaBundle.message("setup.kittitle", kitTitle), () => action(panel))
    panel
  }
}

object AbstractNotificationProvider {

  private[notification] def isSourceCode(file: PsiFile): Boolean =
    file.getLanguage.isKindOf(ScalaLanguage.INSTANCE) &&
      !file.getName.endsWith(Sbt.Extension) && // root sbt files belong to main (not *-build) modules
      file.isWritable
}
