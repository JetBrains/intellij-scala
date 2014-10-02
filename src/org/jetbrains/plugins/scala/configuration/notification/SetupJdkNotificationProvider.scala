package org.jetbrains.plugins.scala
package configuration.notification

import com.intellij.ProjectTopics
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootAdapter, ModuleRootManager, ModuleRootModificationUtil}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.plugins.scala.configuration.notification.SetupJdkNotificationProvider._
import org.jetbrains.plugins.scala.extensions._

/**
 * @author Pavel Fatin
 */
class SetupJdkNotificationProvider(project: Project, notifications: EditorNotifications)
        extends EditorNotifications.Provider[EditorNotificationPanel] {

  project.getMessageBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
    override def rootsChanged(event: ModuleRootEvent) {
      notifications.updateAllNotifications()
    }
  })

  override def getKey = ProviderKey

  override def createNotificationPanel(file: VirtualFile, fileEditor: FileEditor) = {
    val jdk = Option(PsiManager.getInstance(project).findFile(file))
            .filter(_.getLanguage == ScalaLanguage.Instance)
            .flatMap(psiFile => Option(ModuleUtilCore.findModuleForPsiElement(psiFile)))
            .map(module => ModuleRootManager.getInstance(module).getSdk)

    if (jdk.exists(_ == null)) createPanel(project, PsiManager.getInstance(project).findFile(file)) else null
  }
}

object SetupJdkNotificationProvider {
  private val ProviderKey = Key.create[EditorNotificationPanel]("Setup JDK")

  private def createPanel(project: Project, file: PsiFile): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel()
    panel.setText("Project JDK is not defined")
    panel.createActionLabel("Setup JDK", new Runnable {
      override def run() {
        setupSdk(project, file)
      }
    })
    panel
  }

  private def setupSdk(project: Project, file: PsiFile) {
    Option(ProjectSettingsService.getInstance(project).chooseAndSetSdk()).foreach { projectSdk =>
      Option(ModuleUtilCore.findModuleForPsiElement(file)).foreach { module =>
        inWriteAction {
          ModuleRootModificationUtil.setSdkInherited(module)
        }
      }
    }
  }
}