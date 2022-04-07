package org.jetbrains.bsp.project.importing

import com.intellij.openapi.application.{AppUIExecutor, ApplicationManager, ModalityState}
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalProjectSettings, ExternalSystemSettingsListenerEx}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.{ToolWindow, ToolWindowManager}
import com.intellij.util.ui.EdtInvocationManager
import org.jetbrains.bsp.project.BspExternalSystemManager
import org.jetbrains.bsp.settings.BspSettings

import java.util

/**
 * Hack workaround for SCL-19965<br>
 * See very similar hack [[org.jetbrains.sbt.project.ShowSbtShellAfterCreatingNewProject]]
 */
final class ShowBspToolWindowAfterCreatingNewProject extends ExternalSystemSettingsListenerEx {

  override def onProjectsLinked(
    project: Project,
    externalSystemManager: ExternalSystemManager[_, _, _, _, _],
    collection: util.Collection[_ <: ExternalProjectSettings]
  ): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      return

    val bspManager = externalSystemManager match {
      case manager: BspExternalSystemManager => manager
      case _ => return
    }

    val startupManager = StartupManager.getInstance(project)
    val showToolWindow = startupManager.postStartupActivityPassed
    startupManager.runAfterOpened(() => {
      val settings: BspSettings = bspManager.getSettingsProvider.fun(project)

      val bspToolWindow = getBspToolWindow(project)
      if (bspToolWindow != null) {
        activate(bspToolWindow, settings, showToolWindow)
      }
      else {
        //in some rare cases, toolwindow can be non-initialized by this moment ¯\_(ツ)_/¯
        //this hack comes from ExternalToolWindowManager
        AppUIExecutor.onUiThread(ModalityState.NON_MODAL).expireWith(settings).later.execute(() => {
          ToolWindowManager.getInstance(settings.getProject).invokeLater(() => {
            val toolWindow1 = getBspToolWindow(project)
            if (toolWindow1 != null) {
              activate(toolWindow1, settings, showToolWindow)
            }
          })
        })
      }
    })
  }

  override def onProjectsUnlinked(
    project: Project,
    externalSystemManager: ExternalSystemManager[_, _, _, _, _],
    set: util.Set[String]
  ): Unit = {
    val bspManager = externalSystemManager match {
      case manager: BspExternalSystemManager => manager
      case _ => return
    }
    val settings: BspSettings = bspManager.getSettingsProvider.fun(project)

    if (!settings.getLinkedProjectsSettings.isEmpty)
      return

    val toolWindow = getBspToolWindow(project)
    if (toolWindow != null) {
      AppUIExecutor
        .onUiThread
        .expireWith(settings)
        .expireWith(toolWindow.getDisposable)
        .execute(() => toolWindow.setAvailable(false))
    }
  }

  private def getBspToolWindow(project: Project): ToolWindow =
    ToolWindowManager.getInstance(project).getToolWindow("bsp")

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

  override def onProjectsLoaded(
    project: Project,
    externalSystemManager: ExternalSystemManager[_, _, _, _, _],
    collection: util.Collection[_ <: ExternalProjectSettings]
  ): Unit = {}
}
