package org.jetbrains.sbt.project

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.ui.GotItTooltip
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.sbt.project.SeparateMainTestModulesNotificationListener._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtExternalSystemConfigurable
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
class SeparateMainTestModulesNotificationListener(project: Project) extends SbtProjectDataImportListener(project) {

  override def onImportFinished(projectPath: String): Unit = {
    if (!isListenerAllowed(projectPath)) return

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
    val shouldShow = sbtProjectSettings.separateProdAndTestSources && !sbtProjectSettings.separateProdAndTestSourcesIsExplicit
    if (!wasOldNotificationShown && shouldShow) {
      show(project)
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

  private def show(project: Project): Unit = {
    val sbtButton = SbtTooltip.findSbtToolWindowButton(project)
    sbtButton.foreach { button =>
      val toolWindowManagerImpl = SbtTooltip.findToolWindowManagerDisposable(project)

      val gtip = new GotItTooltip(
        "sbt.main.test.modules.enabled",
        SbtBundle.message("separate.modules.main.test.notification"),
        toolWindowManagerImpl.orNull
      )
        .withLink(SbtBundle.message("open.sbt.project.settings"), () =>
          ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[SbtExternalSystemConfigurable], SbtBundle.message("separate.prod.test.modules"))
        )
        .withSecondaryButton(SbtBundle.message("separate.modules.main.test.notification.read"), () => {
          SbtUtil.openSeparateMainTestModulesBlogPost()
          kotlin.Unit.INSTANCE
        })

      val pointProvider = SbtTooltip.tooltipPointOfOrigin(button)
      gtip.show(button, pointProvider)
    }
  }
}
