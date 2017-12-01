package org.jetbrains.plugins.scala.project.notification

import com.intellij.ProjectTopics.PROJECT_ROOTS
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore._
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootAdapter, ModuleRootEvent, ModuleRootListener}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.plugins.scala.extensions.Nullable

/**
  * @author adkozlov
  */
abstract class AbstractNotificationProvider(project: Project, notifications: EditorNotifications)
  extends EditorNotifications.Provider[EditorNotificationPanel] {

  import org.jetbrains.plugins.scala.ScalaLanguage

  project.getMessageBus.connect(project).subscribe(PROJECT_ROOTS, new ModuleRootListener {
    override def rootsChanged(event: ModuleRootEvent) {
      notifications.updateAllNotifications()
    }
  })

  override final def createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel = {
    val psiManager = PsiManager.getInstance(project)
    val maybePanel =
      for {
        psiFile <- psiManager.findFile(file).toOption
        if isSourceCode(psiFile)
        module <- findModuleForPsiElement(psiFile).toOption
        if !hasDeveloperKit(module)
      } yield {
        createPanel(module)
      }
    maybePanel.orNull
  }

  private def isSourceCode(file: PsiFile): Boolean =
    file.getLanguage.isKindOf(ScalaLanguage.INSTANCE) &&
      !file.getName.endsWith(".sbt") && // root sbt files belong to main (not *-build) modules
      file.isWritable

  protected def hasDeveloperKit(module: Module): Boolean

  protected def createTask(module: Module): Runnable

  protected def panelText: String

  protected def developerKitTitle: String

  private def createPanel(module: Module): EditorNotificationPanel = {
    val task = createTask(module)
    val panel = new EditorNotificationPanel().text(panelText)
    panel.createActionLabel(s"Setup $developerKitTitle", task)
    panel
  }
}
