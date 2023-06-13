package org.jetbrains.sbt.project

import com.intellij.openapi.application.{AppUIExecutor, ApplicationManager, ModalityState}
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.settings.{ExternalProjectSettings, ExternalSystemSettingsListenerEx}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.{ToolWindow, ToolWindowManager}
import com.intellij.ui.AppUIUtil
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.SbtShellToolWindowFactory

import java.util

/**
 * This is hacky workaround to show sbt-shell tool window when a new sbt project is created.<br>
 * It was copied from [[com.intellij.openapi.externalSystem.service.ui.ExternalToolWindowManager]]
 *
 * Note that when a new project is being created these methods return `false` (UPD: or rather "sometimes can return", not 100% sure):
 *  - [[org.jetbrains.sbt.shell.SbtShellToolWindowFactory.shouldBeAvailable]]
 *  - [[com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory#shouldBeAvailable]]
 *
 * External tool window is explicitly activated in ExternalToolWindowManager.
 * However "sbt-shell" tool window is not an "external tool window" because it doesn't extend
 * [[com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory]]
 * This is why we need this custom class.
 *
 * related: SCL-19909, IDEA-287117, SCL-20978
 */
final class ShowSbtShellAfterCreatingNewProject extends ExternalSystemSettingsListenerEx {

  override def onProjectsLinked(
    project: Project,
    externalSystemManager: ExternalSystemManager[_, _, _, _, _],
    collection: util.Collection[_ <: ExternalProjectSettings]
  ): Unit = {
    // This method is also called on the "default" project instance, before the new project is created.
    // `StartupManager#runAfterOpened` throws an exception if called on the default project instance.
    if (ApplicationManager.getApplication.isUnitTestMode || project.isDefault)
      return

    val sbtManager = externalSystemManager match {
      case manager: SbtExternalSystemManager => manager
      case _ =>
        return
    }

    StartupManager.getInstance(project).runAfterOpened(() => {
      val settings: SbtSettings = sbtManager.getSettingsProvider.fun(project)

      val sbtShellToolWindow = getSbtShellToolWindowInstance(project)
      if (sbtShellToolWindow != null) {
        setAvailable(sbtShellToolWindow)
      }
      else {
        //in some rare cases, toolwindow can be non-initialized by this moment ¯\_(ツ)_/¯
        //this hack comes from ExternalToolWindowManager
        AppUIExecutor.onUiThread(ModalityState.nonModal()).expireWith(settings).later.execute(() => {
          ToolWindowManager.getInstance(settings.getProject).invokeLater(() => {
            val toolWindow1 = getSbtShellToolWindowInstance(project)
            if (toolWindow1 != null) {
              setAvailable(toolWindow1)
            }
          })
        })
      }
    })
  }

  private def setAvailable(toolWindow: ToolWindow): Unit = {
    if (!toolWindow.isAvailable) {
      AppUIUtil.invokeLaterIfProjectAlive(toolWindow.getProject, () => {
        toolWindow.setAvailable(true)
      })
    }
  }

  private def getSbtShellToolWindowInstance(project: Project): ToolWindow =
    ToolWindowManager.getInstance(project).getToolWindow(SbtShellToolWindowFactory.ID)

  override def onProjectsLoaded(
    project: Project,
    externalSystemManager: ExternalSystemManager[_, _, _, _, _],
    collection: util.Collection[_ <: ExternalProjectSettings]
  ): Unit = {}

  override def onProjectsUnlinked(
    project: Project,
    externalSystemManager: ExternalSystemManager[_, _, _, _, _],
    set: util.Set[String]
  ): Unit = {
    val sbtManager = externalSystemManager match {
      case manager: SbtExternalSystemManager => manager
      case _ =>
        return
    }
    val settings: SbtSettings = sbtManager.getSettingsProvider.fun(project)

    if (!settings.getLinkedProjectsSettings.isEmpty)
      return

    val toolWindow = getSbtShellToolWindowInstance(project)
    if (toolWindow != null) {
      AppUIExecutor
        .onUiThread
        .expireWith(settings)
        .expireWith(toolWindow.getDisposable)
        .execute(() => toolWindow.setAvailable(false))
    }
  }
}
