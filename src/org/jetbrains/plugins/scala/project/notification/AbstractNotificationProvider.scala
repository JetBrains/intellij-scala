package org.jetbrains.plugins.scala.project.notification

import com.intellij.ProjectTopics.PROJECT_ROOTS
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore._
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootAdapter, ModuleRootEvent}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager.getInstance
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.plugins.scala.extensions.ObjectExt

/**
  * @author adkozlov
  */
abstract class AbstractNotificationProvider(project: Project, notifications: EditorNotifications)
  extends EditorNotifications.Provider[EditorNotificationPanel] {

  project.getMessageBus.connect(project).subscribe(PROJECT_ROOTS, new ModuleRootAdapter {
    override def rootsChanged(event: ModuleRootEvent) {
      notifications.updateAllNotifications()
    }
  })

  override final def createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel = {
    val maybeFile = Option(project) map {
      getInstance
    } flatMap {
      _.findFile(file).toOption
    } filter {
      isSourceCode
    }

    val maybeModule = maybeFile flatMap {
      findModuleForPsiElement(_).toOption
    } filterNot {
      hasDeveloperKit
    }

    maybeModule map {
      createTask
    } map {
      createPanel
    } orNull
  }

  protected def isSourceCode(file: PsiFile): Boolean

  protected def hasDeveloperKit(module: Module): Boolean

  protected def createTask(module: Module): Runnable

  protected def panelText: String

  protected def developerKitTitle: String

  private def createPanel(task: Runnable): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel().text(panelText)
    panel.createActionLabel(s"Setup $developerKitTitle", task)
    panel
  }
}
