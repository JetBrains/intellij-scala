package org.jetbrains.sbt.project

import com.intellij.ide.impl.TrustedProjects
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.invokeWhenSmart
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.MigrateConfigurationsDialogWrapper.ModuleConfigurationExt
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.{ModuleConfiguration, ModuleHeuristicResult, getConfigurationToHeuristicResult}
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.settings.ShouldUpdateRunConfigurations


/**
 * Project import listener created to detect whether a notification with upgrade configuration action should be displayed.
 * The notification is displayed only once for non-new sbt projects.
 */
class UpdateConfigurationImportListener(project: Project) extends ProjectDataImportListener {

  private var separateProdTestSources: Boolean = _

  override def onImportStarted(projectPath: String): Unit =
    separateProdTestSources = getSeparateProdTestSourcesValue

  override def onImportFinished(projectPath: String): Unit = {
    val isTrustedProject = TrustedProjects.isTrusted(project)
    if (!(SbtUtil.isSbtProject(project) && isTrustedProject)) return

    val newSeparateProdTestSourcesValue = getSeparateProdTestSourcesValue
    val separateProdTestSourcesChanged = newSeparateProdTestSourcesValue != separateProdTestSources
    val shouldUpdate = shouldUpdateRunConfigurations(project, separateProdTestSourcesChanged)

    // If separate prod/test sources were enabled before the reload and are disabled now,
    // it means this feature has been switched off, indicating a downgrade.
    // For more details check org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.IsDowngradingFromSeparateMainTestModules
    val isDowngrading = separateProdTestSources && !newSeparateProdTestSourcesValue

    // Updating the state before calling #update ensures that if the project is closed before the indexes are ready,
    // the notification will still be displayed when the project is reopened.
    ShouldUpdateRunConfigurations.updateState(project, shouldUpdate, isDowngrading)

    if (shouldUpdate) {
      // If an update is expected, it's better to close all existing notifications first.
      // This prevents the user from interacting with the outdated notification, which could lead
      // to displaying many broken configurations in the dialog.
      // These configurations will likely be automatically updated in the upcoming update method.
      UpdateRunConfigurationsNotification.closeAllExistingNotifications(project)

      UpdateConfigurationImportListener.update(isDowngrading, project)
    }
  }

  // ScalaCompilerConfiguration.separateProdTestSources was initially created to record whether a project was imported
  // with separate modules for production and test, and to use this information when initializing ProjectSettingsImpl.
  // However, it is also useful here because in #onImportStarted, the old value of ScalaCompilerConfiguration.separateProdTestSources is read
  // (before it's updated in SbtProjectDataService.updateSeparateProdTestSources) and in #onImportFinished, the updated value is read.
  private def getSeparateProdTestSourcesValue: Boolean =
    ScalaCompilerConfiguration.instanceIn(project).separateProdTestSources

  private def isNewlyCreatedProject(project: Project): Boolean = {
    val isNew = project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT)
    isNew != null && isNew
  }

  private def shouldUpdateRunConfigurations(project: Project, prodTestSourcesHasChanged: Boolean): Boolean = {
    val isNew = isNewlyCreatedProject(project)
    val isMigrateConfigurationsNotificationShown = ScalaProjectSettings.getInstance(project).isMigrateConfigurationsNotificationShown

    !isNew && (!isMigrateConfigurationsNotificationShown || prodTestSourcesHasChanged)
  }
}

object UpdateConfigurationImportListener {

  def update(isDowngrading: Boolean, project: Project): Unit = {
    // note: we need to wait until the project switches to smart mode before executing
    // this logic, because under the hood it calls JavaExecutionUtil#findMainClass
    // (from com.intellij.execution.configurations.JavaRunConfigurationModule.findNotNullClass),
    // which for a project in a dumb mode returns null so we can get incorrect results.
    invokeWhenSmart(project) {
      val configToHeuristicResult = getConfigurationToHeuristicResult(project, Some(isDowngrading))
      /*
      The listener only updates the run configurations in advance and not whenever the user calls `SbtMigrateConfigurationsAction` because:
       1. The heuristic is less accurate when the user calls this action from all actions - since it's unclear what exactly happened;
          the user might want to adjust some configurations from the old grouping to the new one, or some configurations might need to be downgraded from main/test modules.
       2. If there is an issue with the heuristic, the user can call `SbtMigrateConfigurationsAction` explicitly and manually apply changes to the configuration modules.
      */
      val notModifiedConfigurations = applyHeuristicResultsIfPossible(configToHeuristicResult)
      val containsNonTemporaryConfigs = notModifiedConfigurations.exists {  case (config, _) => !config.isTemporary }
      if (containsNonTemporaryConfigs) {
        showNotification(Some(isDowngrading), project)
      } else {
        ShouldUpdateRunConfigurations.getInstance(project).shouldUpdate = false
      }
      setMigrateNotificationShown(project)
    }
  }

  private def showNotification(isDowngradingFromSeparateMainTestModules: Option[Boolean], project: Project): Unit = {
    UpdateRunConfigurationsNotification.closeAllExistingSuggestions(project)
    val notification = new UpdateRunConfigurationsNotification(project, isDowngradingFromSeparateMainTestModules)
    notification.notify(project)
  }

  private def setMigrateNotificationShown(project: Project): Unit =
    ScalaProjectSettings.getInstance(project).setMigrateConfigurationsNotificationShown(true)

  /**
   * Modifies modules in configurations if the heuristic identifies a single suitable module.
   *
   * @return the rest of configurations that couldn't be updated
   */
  private def applyHeuristicResultsIfPossible(
    configToHeuristicResult: Seq[(ModuleConfiguration, ModuleHeuristicResult)]
  ): Seq[(ModuleConfiguration, ModuleHeuristicResult)] =
    configToHeuristicResult.filter { case (config, ModuleHeuristicResult(moduleOpt, _)) =>
      moduleOpt match {
        case Some(module) =>
          config.setModule(module)
          false
        case _ => true
      }
    }
}
