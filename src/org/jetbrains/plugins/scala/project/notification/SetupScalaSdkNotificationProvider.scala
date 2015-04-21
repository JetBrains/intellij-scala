package org.jetbrains.plugins.scala
package project.notification

import javax.swing.JComponent

import com.intellij.ProjectTopics
import com.intellij.framework.addSupport.impl.AddSupportForSingleFrameworkDialog
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.{JavaModuleType, ModuleUtil, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots._
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.notification.SetupScalaSdkNotificationProvider._
import org.jetbrains.plugins.scala.project.template.ScalaSupportProvider

/**
 * @author Pavel Fatin
 */
class SetupScalaSdkNotificationProvider(project: Project, notifications: EditorNotifications)
        extends EditorNotifications.Provider[EditorNotificationPanel] {

  project.getMessageBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter {
    override def rootsChanged(event: ModuleRootEvent) {
      notifications.updateAllNotifications()
    }
  })

  override def getKey = ProviderKey

  override def createNotificationPanel(file: VirtualFile, fileEditor: FileEditor) = {
    val hasSdk = Option(PsiManager.getInstance(project).findFile(file))
            .filter(_.getLanguage == ScalaLanguage.Instance)
            .filter(!_.getName.endsWith(".sbt")) // root SBT files belong to main (not *-build) modules
            .filter(_.isWritable)
            .flatMap(psiFile => Option(ModuleUtilCore.findModuleForPsiElement(psiFile)))
            .filter(module => ModuleUtil.getModuleType(module) == JavaModuleType.getModuleType)
            .filter(!_.getName.endsWith("-build")) // gen-idea doesn't use the SBT module type
            .map(module => module.hasScala)

    if (hasSdk.contains(false)) createPanel(project, PsiManager.getInstance(project).findFile(file)) else null
  }
}

object SetupScalaSdkNotificationProvider {
  private val ProviderKey = Key.create[EditorNotificationPanel]("Setup Scala SDK")

  private def createPanel(project: Project, file: PsiFile): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel()
    panel.setText("No Scala SDK in module")
    panel.createActionLabel("Setup Scala SDK", new Runnable {
      override def run() {
        setupSdk(panel, project, file)
      }
    })
    panel
  }

  private def setupSdk(parent: JComponent, project: Project, file: PsiFile) {
    Option(ModuleUtilCore.findModuleForPsiElement(file)).foreach { module =>
      val dialog = AddSupportForSingleFrameworkDialog.createDialog(module, new ScalaSupportProvider())
      dialog.showAndGet()
    }
  }
}