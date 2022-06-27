package org.jetbrains.sbt.project

import com.intellij.openapi.application.{AppUIExecutor, ApplicationManager, ModalityState}
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalProjectSettings, ExternalSystemSettingsListenerEx}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.{ToolWindow, ToolWindowManager}
import com.intellij.util.ui.EdtInvocationManager
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.SbtShellToolWindowFactory

import java.util

/**
 * This is hacky workaround to show sbt-shell tool window when a new project is created.
 * It was copied from [[com.intellij.openapi.externalSystem.service.ui.ExternalToolWindowManager]]
 *
 * Note that when a new project is being created<br>
 * SbtShellToolWindowFactory.shouldBeAvailable and
 * AbstractExternalSystemToolWindowFactory.shouldBeAvailable return `false`
 * External tool window is explicitly activated in ExternalToolWindowManager.<br>
 *
 * related: SCL-19909, IDEA-287117
 */
final class ShowSbtShellAfterCreatingNewProject extends ExternalSystemSettingsListenerEx {

  override def onProjectsLinked(
    project: Project,
    externalSystemManager: ExternalSystemManager[_, _, _, _, _],
    collection: util.Collection[_ <: ExternalProjectSettings]
  ): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      return

    import scala.language.existentials

    val sbtManager = externalSystemManager match {
      case manager: SbtExternalSystemManager => manager
      case _ => return
    }

    val startupManager = StartupManager.getInstance(project)
    val showToolWindow = startupManager.postStartupActivityPassed
    startupManager.runAfterOpened(() => {
      val settings: SbtSettings = sbtManager.getSettingsProvider.fun(project)

      val sbtShellToolWindow = getSbtShellToolWindowInstance(project)
      if (sbtShellToolWindow != null) {
        activate(sbtShellToolWindow, settings, showToolWindow)
      }
      else {
        //in some rare cases, toolwindow can be non-initialized by this moment ¯\_(ツ)_/¯
        //this hack comes from ExternalToolWindowManager
        AppUIExecutor.onUiThread(ModalityState.NON_MODAL).expireWith(settings).later.execute(() => {
          ToolWindowManager.getInstance(settings.getProject).invokeLater(() => {
            val toolWindow1 = getSbtShellToolWindowInstance(project)
            if (toolWindow1 != null) {
              activate(toolWindow1, settings, showToolWindow)
            }
          })
        })
      }
    })
  }

  private def activate(toolWindow: ToolWindow, settings: AbstractExternalSystemSettings[_, _, _], showToolWindow: Boolean): Unit = {
    val condition = toolWindow.isAvailable && !showToolWindow
    if (condition)
      return

    EdtInvocationManager.invokeLaterIfNeeded(() => {
      val shouldShow = showToolWindow && settings.getLinkedProjectsSettings.size == 1 &&
        settings.getProject.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == null
      toolWindow.setAvailable(true, () => {
        if (shouldShow) {
          toolWindow.show()
        }
      })
    })
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
      case _ => return
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
