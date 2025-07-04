package org.jetbrains.sbt.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.{ActionButton, ActionButtonUtil}
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.{SquareStripeButton, ToolWindowManagerImpl}
import com.intellij.openapi.wm.{ToolWindowManager, WindowManager}

private object SbtTooltip {
  def findSbtToolWindowButton(project: Project): Option[ActionButton] = {
    val frame = WindowManager.getInstance().getIdeFrame(project)
    if (frame == null) return None
    val component = frame.getComponent
    val maybeActionButton = ActionButtonUtil.findActionButton(component, {
      case button: SquareStripeButton => button.getToolWindow.getStripeTitle == "sbt"
      case _ => false
    })

    Option(maybeActionButton)
  }

  //noinspection ApiStatus,UnstableApiUsage
  def findToolWindowManagerDisposable(project: Project): Option[Disposable] =
    Option(ToolWindowManager.getInstance(project))
      .collect { case x: ToolWindowManagerImpl => x }

  def openSbtProjectSettings(project: Project, externalProjectPath: String): Unit = {
    val manager = ExternalSystemApiUtil.getManager(SbtProjectSystem.Id)
    val configurable = manager.asInstanceOf[ExternalSystemConfigurableAware].getConfigurable(project)
    configurable match {
      case x: AbstractExternalSystemConfigurable[_, _, _] =>
        ShowSettingsUtil.getInstance().editConfigurable(project, x, () => x.selectProject(externalProjectPath))
      case _ =>
    }
  }
}
