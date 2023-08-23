package org.jetbrains.plugins.scala.project.notification

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.impl.AddSupportForSingleFrameworkDialog
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.ui.{EditorNotificationPanel, EditorNotificationProvider}
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.template.ScalaFrameworkType

import java.util.function
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

/**
 * For other examples see:
 *  - [[com.intellij.codeInsight.daemon.impl.SdkSetupNotificationProvider]]
 *  - [[com.intellij.codeInsight.daemon.ProjectSdkSetupValidator]]
 *  - [[com.intellij.codeInsight.daemon.impl.JavaProjectSdkSetupValidator]]
 */
final class SetupScalaSdkNotificationProvider extends EditorNotificationProvider {

  override def collectNotificationData(project: Project, file: VirtualFile): function.Function[_ >: FileEditor, _ <: JComponent] = {
    val isScalaSource = isScalaSourceFile(file, project)
    if (isScalaSource && !hasDeveloperKit(file, project))
      (fileEditor: FileEditor) => createPanel(project, fileEditor)
    else
      null
  }

  @RequiresEdt
  private def createPanel(project: Project, fileEditor: FileEditor): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
    panel.setText(ScalaBundle.message("sdk.notification.provider.no.scala.sdk.in.module"))
    val fixHandler = getFixHandler(project, fileEditor.getFile)
    panel.createActionLabel(ScalaBundle.message("sdk.notification.provider.setup.scala.sdk"), fixHandler, true)
    panel
  }

  private def getFixHandler(project: Project, file: VirtualFile): EditorNotificationPanel.ActionHandler =
    new EditorNotificationPanel.ActionHandler() {
      override def handlePanelActionClick(panel: EditorNotificationPanel, e: HyperlinkEvent): Unit = {
        setDeveloperKit(file, project)
      }

      override def handleQuickFixClick(editor: Editor, psiFile: PsiFile): Unit = {
        setDeveloperKit(file, project)
      }
    }

  private def hasDeveloperKit(file: VirtualFile, project: Project): Boolean =
    findModule(file, project).forall { module =>
      ModuleType.get(module) != JavaModuleType.getModuleType ||
        module.isBuildModule || // gen-idea doesn't use the sbt module type
        module.hasScala
    }

  private def setDeveloperKit(file: VirtualFile, project: Project): Unit = {
    findModule(file, project).foreach { module =>
      val scalaFrameworkType = FrameworkTypeEx.EP_NAME.findExtension(classOf[ScalaFrameworkType])
      val dialog = AddSupportForSingleFrameworkDialog.createDialog(module, scalaFrameworkType.createProvider)
      dialog.showAndGet()
    }
  }

  private def findModule(file: VirtualFile, project: Project): Option[Module] =
    Option(ModuleUtilCore.findModuleForFile(file, project))
}
