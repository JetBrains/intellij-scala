package org.jetbrains.sbt.project

import com.intellij.framework.FrameworkTypeEx
import com.intellij.framework.addSupport.impl.AddSupportForSingleFrameworkDialog
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.IncompleteModelUtil
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.ui.{EditorNotificationPanel, EditorNotificationProvider}
import com.intellij.util.concurrency.annotations.{RequiresEdt, RequiresReadLock}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.notification.isScalaSourceFile
import org.jetbrains.plugins.scala.project.template.ScalaFrameworkType
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.codeInsight.daemon.SbtProjectImportStateProblemHighlightFilter

import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

/**
 * For other examples see:
 *  - [[com.intellij.codeInsight.daemon.impl.SdkSetupNotificationProvider]]
 *  - [[com.intellij.codeInsight.daemon.ProjectSdkSetupValidator]]
 *  - [[com.intellij.codeInsight.daemon.impl.JavaProjectSdkSetupValidator]]
 */
private final class SetupScalaHighlightingNotificationProvider extends EditorNotificationProvider {

  @RequiresReadLock
  @Nullable
  override def collectNotificationData(project: Project, file: VirtualFile): java.util.function.Function[_ >: FileEditor, _ <: JComponent] = {
    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile eq null) return null

    if (SbtProjectUtil.isInSbtProject(psiFile)) {
      // We do not track the file type of this source file, do not show any notifications.
      if (!SbtProjectImportStateProblemHighlightFilter.isTrackedFileType(file.getFileType)) return null
      // Project reload is already in progress, do not show any notifications.
      //noinspection ApiStatus,UnstableApiUsage
      if (IncompleteModelUtil.isIncompleteModel(psiFile)) return null

      // The currently open file is not a writable project source, do not show any notifications.
      // This check filters out files created from reconstructed VCS history,
      // which do not seem to belong to any IDE module and thus would return a "not imported" project state.
      // This check also filters out library source files.
      // These seem tough and could result in false positive notification banners being shown, because the project
      // could be in a very strange imported state, if the user had manually scrambled the project structure.
      // The original ticket scope also only considers writable project source files, so let's reconsider this
      // decision in the future, when it becomes necessary.
      if (!SbtProjectImportStateProblemHighlightFilter.isWritableSourceFile(psiFile)) return null

      // The project is fully imported, do not show any notifications.
      if (SbtProjectImportStateService.instance(project).isImported(psiFile)) return null

      (fileEditor: FileEditor) => SbtProjectImportStateNotificationPanel.createNotificationPanel(project, fileEditor)
    } else {
      val isScalaSource = isScalaSourceFile(file, project)
      if (isScalaSource && !hasDeveloperKit(file, project)) {
        // No notification while project sync is in progress
        //noinspection ApiStatus,UnstableApiUsage
        if (IncompleteModelUtil.isIncompleteModel(psiFile)) {
          null
        } else {
          (fileEditor: FileEditor) => createPanel(project, fileEditor)
        }
      } else
        null
    }
  }

  @RequiresEdt
  private def createPanel(project: Project, fileEditor: FileEditor): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
    panel.setText(SbtBundle.message("sdk.notification.provider.no.scala.sdk.in.module"))
    val fixHandler = getFixHandler(project, fileEditor.getFile)
    panel.createActionLabel(SbtBundle.message("sdk.notification.provider.setup.scala.sdk"), fixHandler, true)
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
