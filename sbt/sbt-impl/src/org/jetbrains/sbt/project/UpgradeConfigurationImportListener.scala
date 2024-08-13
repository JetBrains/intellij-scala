package org.jetbrains.sbt.project

import com.intellij.ide.impl.TrustedProjects
import com.intellij.notification.{NotificationAction, NotificationGroupManager, NotificationType}
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.{DumbService, Project}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.SbtModuleType
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.isConfigurationInvalid
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

/**
 * Project import listener created to detect whether a notification with upgrade configuration action should be displayed.
 * The notification is displayed only once for non-new sbt projects.
 */
class UpgradeConfigurationImportListener(project: Project) extends ProjectDataImportListener {

  private var switchedFromMainTestModules: Boolean = _

  override def onImportStarted(projectPath: String): Unit = {
    switchedFromMainTestModules = isNotForThisListener
    super.onImportStarted(projectPath)
  }

  private def isNotForThisListener: Boolean = {
    val isProdTestSeparated = SbtUtil.isBuiltWithSeparateModulesForProdTest(project)
    val modules = module.ModuleManager.getInstance(project).getModules.map(ExternalSystemModulePropertyManager.getInstance(_).getExternalModuleType)
    // it means that now we are switching on the separate modules for prod/test
    val is1 = isProdTestSeparated && !modules.contains(SbtModuleType.sbtSourceSetModuleType)
    // it means that now we are switching off separate modules for prod/test
    val is2 = !isProdTestSeparated && modules.contains(SbtModuleType.sbtSourceSetModuleType)
    is1 || is2
  }

  override def onImportFinished(projectPath: String): Unit = {
    val isTrustedProject = TrustedProjects.isTrusted(project)
    val isProdTestSeparated = SbtUtil.isBuiltWithSeparateModulesForProdTest(project)
    val modules = module.ModuleManager.getInstance(project).getModules.map(ExternalSystemModulePropertyManager.getInstance(_).getExternalModuleType)
    if (!(SbtUtil.isSbtProject(project) && isTrustedProject)) return

    // note: we need to wait until the project switches to smart mode before executing
    // this logic, because under the hood it calls JavaExecutionUtil#findMainClass
    // (from com.intellij.execution.configurations.JavaRunConfigurationModule.findNotNullClass),
    // which for a project in a dumb mode returns null so we can get incorrect results.
    // TODO it could be written better with coroutineScope.launch && smartReadAction
    DumbService.getInstance(project).runWhenSmart(() => {
      if (shouldShowNotification(project)) {
        showNotification()
      }
      setNotificationShown()
    })
  }

  private def isNewlyCreatedProject(project: Project): Boolean = {
    val isNew = project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT)
    isNew != null && isNew
  }

  private def shouldShowNotification(project: Project): Boolean = {
    val isNew = isNewlyCreatedProject(project)
    val isProdTestSeparated = SbtUtil.isBuiltWithSeparateModulesForProdTest(project)
    val shouldShowBecauseOfIncorrectGrouping = !isNotificationAlreadyShown && areIncorrectConfigurationsPresent
    val shouldShowBecauseSeparateModulesForProdTestSwitched = switchedFromMainTestModules
    shouldShowBecauseSeparateModulesForProdTestSwitched || (!isNew && shouldShowBecauseOfIncorrectGrouping)
  }

  private def showNotification():Unit = {
    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("sbt.configuration.migration")
    val notification = notificationGroup.createNotification(SbtBundle.message("sbt.configuration.migration.notification.content"), NotificationType.WARNING)

//    val action = ActionManager.getInstance.getAction(SbtMigrateConfigurationsAction.ID)\
    val action = new MyNotificationAction(switchedFromMainTestModules)
    notification.addAction(action)

    val ignoreAction = NotificationAction.createSimpleExpiring(SbtBundle.message("sbt.configuration.migration.notification.ignore.text"), () => notification.expire())
    notification.addAction(ignoreAction)

    notification.notify(project)
  }

  private def isNotificationAlreadyShown: Boolean =
    ScalaProjectSettings.getInstance(project).isMigrateConfigurationsNotificationShown

  private def setNotificationShown(): Unit =
    ScalaProjectSettings.getInstance(project).setMigrateConfigurationsNotificationShown(true)

  private def areIncorrectConfigurationsPresent: Boolean = {
    val moduleBasedConfigurations = SbtUtil.getAllModuleBasedConfigurationsInProject(project)
    val prodTestSourcesSeparated = SbtUtil.isBuiltWithSeparateModulesForProdTest(project)

    moduleBasedConfigurations.exists { config =>
      val configurationModule = config.getConfigurationModule
      val oldModuleName = configurationModule.getModuleName
      isConfigurationInvalid(config, configurationModule, oldModuleName, prodTestSourcesSeparated)
    }
  }
}
