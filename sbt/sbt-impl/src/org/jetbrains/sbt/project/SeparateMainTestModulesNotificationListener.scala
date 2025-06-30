package org.jetbrains.sbt.project

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.impl.{ActionButton, ActionButtonUtil}
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.wm.impl.{SquareStripeButton, ToolWindowManagerImpl}
import com.intellij.openapi.project.Project
import com.intellij.ui.GotItTooltip
import com.intellij.openapi.wm.{ToolWindowManager, WindowManager}
import org.jetbrains.sbt.project.SeparateMainTestModulesNotificationListener._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

/**
 * Listener that handles the display of notifications for separate main/test modules feature
 * after project data import is completed.
 *
 * The notification will inform users about the separate modules setting for main and test sources,
 * providing options to:
 *  - Read more about this feature in the blog post
 *  - Access sbt project settings to revert this if needed
 */
class SeparateMainTestModulesNotificationListener(project: Project) extends ProjectDataImportListener {

  override def onImportFinished(projectPath: String): Unit = {
    val sbtProjectSettings = SbtProjectSettings.`for`(project, projectPath)
    sbtProjectSettings.foreach(showNotificationIfNecessary(_, project))
  }
}

object SeparateMainTestModulesNotificationListener {
  private val Key = "sbt.separate.main.test.modules.notification.shown"

  /**
   * Displays the separate modules notification if all the following conditions are met:
   *  - The project is trusted
   *  - The project is not in preview mode
   *  - The separate sources setting is enabled but not explicitly set by user
   *  - The notification hasn't been shown before
   */
  def showNotificationIfNecessary(sbtProjectSettings: SbtProjectSettings, project: Project): Unit = {
    if (!wasOldNotificationShown && shouldShow(sbtProjectSettings, project)) {
      show(sbtProjectSettings.getExternalProjectPath, project)
    }
  }

  /**
   * Checks whether the old main/test modules notification has already been shown to the user.
   * If it has, the new "got it" tooltip won't be displayed.
   */
  private def wasOldNotificationShown: Boolean = {
    // The key construction is taken from com.intellij.ide.util.RunOnceUtilKt.createKey
    val key = s"RunOnceActivity.$Key"
    PropertiesComponent.getInstance().isValueSet(key)
  }

  private def shouldShow(sbtProjectSettings: SbtProjectSettings, project: Project): Boolean = {
    val isTrusted = TrustedProjects.isProjectTrusted(project)
    isTrusted && {
      val isPreview = SbtUtil.isPreview(project, sbtProjectSettings.getExternalProjectPath)
      !isPreview && sbtProjectSettings.separateProdAndTestSources && !sbtProjectSettings.separateProdAndTestSourcesIsExplicit
    }
  }

  private def show(projectPath: String, project: Project): Unit = {
    val sbtButton = findSbtToolWindowButton(project)
    sbtButton.foreach { button =>
      val toolWindowManagerImpl = Option(ToolWindowManager.getInstance(project))
        .collect { case x: ToolWindowManagerImpl => x }
        .orNull

      val gtip = new GotItTooltip(
        "sbt.main.test.modules.enabled",
        SbtBundle.message("separate.modules.main.test.notification"),
        toolWindowManagerImpl
      )
        .withLink(SbtBundle.message("open.sbt.project.settings"), () => openSbtProjectSettings(project, projectPath))
        .withSecondaryButton(SbtBundle.message("separate.modules.main.test.notification.learn"), () => {
          SbtUtil.openSeparateMainTestModulesBlogPost()
          kotlin.Unit.INSTANCE
        })

      gtip.show(button, GotItTooltip.LEFT_MIDDLE)
    }
  }

  private def findSbtToolWindowButton(project: Project): Option[ActionButton] = {
    val frame = WindowManager.getInstance().getIdeFrame(project)
    if (frame == null) return None
    val component = frame.getComponent
    val maybeActionButton = ActionButtonUtil.findActionButton(component, {
      case button: SquareStripeButton => button.getToolWindow.getStripeTitle == "sbt"
      case _ => false
    })

    Option(maybeActionButton)
  }

  private def openSbtProjectSettings(project: Project, externalProjectPath: String): Unit = {
    val manager = ExternalSystemApiUtil.getManager(SbtProjectSystem.Id)
    val configurable = manager.asInstanceOf[ExternalSystemConfigurableAware].getConfigurable(project)
    configurable match {
      case x: AbstractExternalSystemConfigurable[_, _, _] =>
        ShowSettingsUtil.getInstance().editConfigurable(project, x, () => x.selectProject(externalProjectPath))
      case _ =>
    }
  }
}
