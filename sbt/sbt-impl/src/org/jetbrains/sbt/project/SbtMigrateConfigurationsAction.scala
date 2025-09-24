package org.jetbrains.sbt.project

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.{JavaRunConfigurationModule, ModuleBasedConfiguration, RunConfigurationModule}
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.application.CoroutinesKt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleManager, ModuleType, ModuleTypeManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiDocumentManager, PsiElement}
import kotlin.coroutines.Continuation
import kotlinx.coroutines.{BuildersKt, Dispatchers}
import org.jetbrains.annotations.{Nullable, VisibleForTesting}
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.{SbtBundle, SbtUtil}
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.updateRunConfigurations
import org.jetbrains.sbt.project.extensionPoints.ModuleBasedConfigurationDetailsExtractor

import scala.jdk.CollectionConverters.ListHasAsScala

class SbtMigrateConfigurationsAction extends AnAction(
  SbtBundle.message("sbt.migrate.configurations.full.title")
) {

  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    val project = e.getProject
    if (project == null || !SbtUtil.isSbtProject(project)) {
      presentation.setEnabled(false)
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    val areAllConfigurationsUpdated = updateRunConfigurations(project, isDowngradingFromMainTestModules = None)
    // If all configurations have been updated, then remove notifications (as they are no longer relevant)
    if (areAllConfigurationsUpdated) {
      UpdateRunConfigurationsNotification.closeAllExistingSuggestions(project)
    }
  }
}

object SbtMigrateConfigurationsAction {
  val ID = "Scala.Sbt.MigrateConfigurations"
  private val logger: Logger = Logger.getInstance(classOf[SbtMigrateConfigurationsAction])
  private val MAX_MODULES_THRESHOLD = 10

  /**
   * @param isDowngradingFromMainTestModules indicates whether there is a downgrade from main/test modules.
   *                                         If false, then it might be an upgrade to main/test modules or simply a grouping scheme migration.
   *                                         If it's <code>None</code>, the run configurations update is triggered by an explicit action.
   *                                         Providing such information allows the heuristics to work more accurately.
   * @return true if all configurations have been updated, or if there were no configurations to update.
   */
  def updateRunConfigurations(@Nullable project: Project, isDowngradingFromMainTestModules: Option[Boolean]): Boolean = {
    if (project == null || project.isDefault) return true

    val modules = classPathProviderModules(project)
    val configToHeuristicResult = getConfigurationToHeuristicResult(project, modules, isDowngradingFromMainTestModules)

    if (configToHeuristicResult.isEmpty) {
      Messages.showWarningDialog(project, SbtBundle.message("sbt.migrate.configurations.warning.message"), SbtBundle.message("sbt.migrate.configurations.warning.title"))
      true
    } else {
      val dialogWrapper = new MigrateConfigurationsDialogWrapper(modules, configToHeuristicResult.toMap)
      val changedConfigToModule = dialogWrapper.open()
      changedConfigToModule.foreach { case (config, module) =>
        config.setModule(module)
        logger.info(s"In ${config.getName} configuration, the module was changed to ${module.getName}")
      }
      changedConfigToModule.size == configToHeuristicResult.size
    }
  }

  type ModuleConfiguration = ModuleBasedConfiguration[? <: RunConfigurationModule, ?]

  /**
   * @param module single suitable module for configuration identified using heuristic from [[org.jetbrains.sbt.project.SbtMigrateConfigurationsAction#mapConfigurationToHeuristicResult]]
   * @param guesses contains all possible modules (max 10), if heuristic in [[org.jetbrains.sbt.project.SbtMigrateConfigurationsAction#mapConfigurationToHeuristicResult]]
   *                won't be able to find a single suitable module for configuration
   */
  case class ModuleHeuristicResult(module: Option[Module], guesses: Seq[String] = Seq.empty)

  /**
   * @param oldModuleName module name saved in the configuration
   */
  case class ConfigDetails(isTest: Boolean, oldModuleName: String, mainClassName: Option[String])

  object ConfigDetails {
    def apply(config: ModuleBasedConfiguration[?, ?], oldModuleName: String): ConfigDetails = {
      val isTestConfig = ModuleBasedConfigurationDetailsExtractor.isTestConfiguration(config)
      val mainClass = extractMainClassName(config)

      ConfigDetails(isTestConfig, oldModuleName, mainClass)
    }
  }

  def getConfigurationToHeuristicResult(
    project: Project,
    isDowngradingFromMainTestModules: Option[Boolean]
  ): Seq[(ModuleConfiguration, ModuleHeuristicResult)] = {
    val modules = classPathProviderModules(project)
    getConfigurationToHeuristicResult(project, modules, isDowngradingFromMainTestModules)
  }

  private def getConfigurationToHeuristicResult(
    project: Project,
    modules: Seq[Module],
    isDowngradingFromMainTestModules: Option[Boolean]
  ): Seq[(ModuleConfiguration, ModuleHeuristicResult)] = {
    val prodTestSourcesSeparated = SbtUtil.isBuiltWithSeparateModulesForProdTest(project)
    val moduleBasedConfigurations = getAllModuleBasedConfigurationsInProject(project)

    for {
      config <- moduleBasedConfigurations
      configurationModule = config.getConfigurationModule
      oldModuleName = configurationModule.getModuleName
      if isConfigurationInvalid(config, configurationModule.getModule, oldModuleName, project, prodTestSourcesSeparated, isDowngradingFromMainTestModules)
    } yield config -> mapConfigurationToHeuristicResult(config, oldModuleName, modules, project, prodTestSourcesSeparated, isDowngradingFromMainTestModules)
  }

  private def getAllModuleBasedConfigurationsInProject(project: Project): Seq[ModuleConfiguration] = {
    val allConfigurations = RunManager.getInstance(project).getAllConfigurationsList.asScala.toSeq
    allConfigurations.filterByType[ModuleConfiguration]
  }

  private def classPathProviderModules(project: Project): Seq[Module] = {
    val modules = ModuleManager.getInstance(project).getModules.toSeq
    val moduleTypeManager = ModuleTypeManager.getInstance()
    // note: it is written based on com.intellij.execution.ui.ModuleClasspathCombo.isModuleAccepted
    // I didn't use ModuleClasspathCombo directly in the org.jetbrains.sbt.project.MigrateConfigurationsDialogWrapper.myTable,
    // cause it will require additional non-obvious tricks to display it nicely it in a table cell.
    modules.filter(m => moduleTypeManager.isClasspathProvider(ModuleType.get(m)))
  }

  private def hasMainOrTestSuffix(moduleName: String): Boolean =
    moduleName.endsWith(".main") || moduleName.endsWith(".test")

  private def isConfigurationInvalid(
    config: ModuleBasedConfiguration[?, ?],
    @Nullable configurationModule: Module,
    oldModuleName: String,
    project: Project,
    prodTestSourcesSeparated: Boolean,
    isDowngradingFromMainTestModules: Option[Boolean]
  ): Boolean = {
    // If the configuration doesn't have a module, its name is empty, and it is considered to be correct.
    if (oldModuleName.isEmpty) return false

    lazy val isBrokenByModuleGrouping = configurationModule == null || isMainClassAbsentInConfigurationModule(config, project)
    // During an upgrade from the new grouping to main/test modules,
    // the configuration is considered broken if the module exists but is not a sbt source module.
    // For instance, if the configuration initially had the module dummy.foo, after the upgrade, dummy.foo still exists
    // as a parent module but is not a main/test module, which makes the configuration invalid.
    lazy val isBrokenByProdTestSourcesUpgrade = prodTestSourcesSeparated && configurationModule != null && !configurationModule.isSbtSourceSetModule

    lazy val areUpgradingConditionsMet = isBrokenByProdTestSourcesUpgrade || isBrokenByModuleGrouping

    lazy val isBrokenByProdTestSourcesDowngrade = configurationModule == null && hasMainOrTestSuffix(oldModuleName)

    isDowngradingFromMainTestModules match {
      case Some(isDowngrading) if isDowngrading =>
        isBrokenByProdTestSourcesDowngrade
      case _ =>
        // If `isDowngradingFromSeparateMainTestModules` is false or None, it is enough to check upgrading conditions.
        // This is because `isBrokenByModuleGrouping` verifies whether `configurationModule` is null,
        // which also accounts for cases where there has been a downgrade of modules (but it's less accurate than `isBrokenByProdTestSourcesDowngrade`).
        areUpgradingConditionsMet
    }
  }

  /**
   * Checks whether a configuration module doesn't contain an expected main class.
   * If yes, a situation like that could happen - in the old grouping there may have been an IDEA module called X
   * owned by project Y (project in the sbt sense), and in the new grouping the same module (X) may belong to another project e.g. Z.
   * In that case, the configuration that had a module called X will still have it, but it will no longer be the same module as the original one and
   * some main class may no longer exist inside it.
   */
  private def isMainClassAbsentInConfigurationModule(config: ModuleBasedConfiguration[?, ?], project: Project): Boolean = {
    val mainClassNameOpt = extractMainClassName(config)
    val javaRunConfigurationModuleOpt = extractJavaRunConfigurationModule(config)

    (mainClassNameOpt, javaRunConfigurationModuleOpt) match {
      case (Some(mainClass), Some(javaRunConfigurationModule)) if mainClass.nonEmpty =>
        runSmartReadAction(project) {
          val psiClass = javaRunConfigurationModule.findClass(mainClass)
          psiClass == null
        }
      case _ => false
    }
  }

  /**
   * @param oldModuleName the module name saved in the run configuration
   * @param modules include only classpath provider modules (it doesn't contain shared sources or build modules)
   */
  private def mapConfigurationToHeuristicResult(
    config: ModuleBasedConfiguration[?, ?],
    oldModuleName: String,
    modules: Seq[Module],
    project: Project,
    prodTestSourcesSeparated: Boolean,
    isDowngradingFromMainTestModules: Option[Boolean]
  ): ModuleHeuristicResult  = {
    // If separate modules for prod/test are enabled, then only main/test modules should be considered
    val possibleModules =
      if (prodTestSourcesSeparated) modules.filter(_.isSbtSourceSetModule)
      else modules

    val configDetails = ConfigDetails(config, oldModuleName)
    val resultingModules = findModulesForConfig(configDetails, possibleModules, project, prodTestSourcesSeparated, isDowngradingFromMainTestModules)

    resultingModules match {
      case Seq(head) => ModuleHeuristicResult(Some(head))
      // When there are more than 10 resultingModules, displaying them in a tooltip can introduce additional chaos
      case _ if resultingModules.size < MAX_MODULES_THRESHOLD => ModuleHeuristicResult(None, resultingModules.map(_.getName))
      case _  =>
        logWarning(resultingModules, config.getName)
        ModuleHeuristicResult(None)
    }
  }

  private def findModulesForConfig(
    config: ConfigDetails,
    modules: Seq[Module],
    project: Project,
    prodTestSourcesSeparated: Boolean,
    isDowngradingFromMainTestModules: Option[Boolean]
  ): Seq[Module] = {
    val moduleNameToModule = modules.map(m => m.getName -> m).toMap

    def _findModuleWithMainClass(mainClassName: String, moduleNames: Seq[String]): Option[String] = {
      val modules = moduleNames.flatMap(moduleNameToModule.get)
      val moduleContainingMainClass = findModuleWithMainClass(mainClassName, modules, project)
      moduleContainingMainClass.map(_.getName)
    }

    def _findAllModulesWithMainClass(mainClassName: String): Seq[String] = {
      val foundModules = findAllModulesWithMainClass(mainClassName, project)
      // foundModules may contain modules of types like SharedSourcesModuleType,
      // so finding the JVM representation is necessary.
      foundModules.flatMap(_.findJVMModule).map(_.getName)
    }

    val resultingModuleNames = doFindModulesForConfig(
      config,
      moduleNameToModule.keys.toSeq,
      prodTestSourcesSeparated,
      isDowngradingFromMainTestModules,
      _findModuleWithMainClass,
      _findAllModulesWithMainClass
    )

    resultingModuleNames.flatMap(moduleNameToModule.get)
  }

  /**
   * @return suitable module names for configuration found using heuristics
   */
  @VisibleForTesting
  def doFindModulesForConfig(
    config: ConfigDetails,
    moduleNames: Seq[String],
    prodTestSourcesSeparated: Boolean,
    isDowngradingFromMainTestModules: Option[Boolean],
    findModuleWithMainClass: (String, Seq[String]) => Option[String],
    findAllModulesWithMainClass: String => Seq[String]
  ): Seq[String] = {
    val compatibleModuleNames = moduleNames.filter { moduleName =>
      isConfigurationCompatibleWithModuleName(config, moduleName, prodTestSourcesSeparated, isDowngradingFromMainTestModules)
    }

    config.mainClassName match {
      case Some(mainClass) =>
        matchCompatibleModulesWithMainClass(mainClass, compatibleModuleNames, findModuleWithMainClass, findAllModulesWithMainClass)
      case None => compatibleModuleNames
    }
  }

  private def matchCompatibleModulesWithMainClass(
    mainClass: String,
    compatibleModuleNames: Seq[String],
    findModuleContainingMainClass: (String, Seq[String]) => Option[String],
    getAllModulesForMainClass: String => Seq[String]
  ): Seq[String] = {
    val foundModule =
      if (compatibleModuleNames.nonEmpty) findModuleContainingMainClass(mainClass, compatibleModuleNames)
      else None
    foundModule match {
      case Some(module) => Seq(module)
      case None =>
        val allModulesForMainClass = getAllModulesForMainClass(mainClass)
        if (allModulesForMainClass.size == 1) allModulesForMainClass
        else {
          (allModulesForMainClass ++ compatibleModuleNames).distinct
        }
    }
  }

  private def findModuleWithMainClass(mainClassName: String, modules: Seq[Module], project: Project): Option[Module] =
    runSmartReadActionWithCommit(project) {
      modules.find { module =>
        val scope = GlobalSearchScope.moduleScope(module)
        val classes = findAllPsiElementsForMainClassInScope(project, mainClassName, scope)
        classes.nonEmpty
      }
    }

  /**
   * It's written based on [[com.intellij.execution.configurations.JavaRunConfigurationModule#getModulesForClass]].
   * I decided not to use it directly, because it also adds dependant modules to the result. In theory it is also possible
   * to run some configurations in dependant modules, but it doesn't seem to be common practice.
   */
  private def findAllModulesWithMainClass(mainClassName: String, project: Project): Seq[Module] =
    runSmartReadActionWithCommit(project) {
      val scope = GlobalSearchScope.projectScope(project)
      val possibleClasses = findAllPsiElementsForMainClassInScope(project, mainClassName, scope)
      possibleClasses.map(ModuleUtilCore.findModuleForPsiElement).filter(_ != null)
    }

  private def runSmartReadActionWithCommit[T](project: Project)(action: => T): T = {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    runSmartReadAction(project)(action)
  }

  private def runSmartReadAction[T](project: Project)(action: => T): T =
    BuildersKt.runBlocking(
      Dispatchers.getDefault,
      (_, cont: Continuation[? >: T]) => CoroutinesKt.smartReadAction(project, () => action, cont)
    )

  private def findAllPsiElementsForMainClassInScope(project: Project, mainClassName: String, scope: GlobalSearchScope): Seq[PsiElement] = {
    val javaPsiFacadeElements = JavaPsiFacade.getInstance(project).findClasses(mainClassName, scope).toSeq
    val scala3MainElements = ScalaIndexKeys.ANNOTATED_MAIN_FUNCTION_BY_PKG_KEY.elements(mainClassName, scope)(using project).toSeq
    javaPsiFacadeElements ++ scala3MainElements
  }

  private def logWarning(resultingModules: Seq[Module], configName: String): Unit = {
    val moduleNames = resultingModules.map(_.getName).mkString(",")
    logger.warn(s"For $configName the update configuration action found 10 or more modules: $moduleNames")
  }

  /**
   * Determines whether the given configuration's old module name is compatible with the specified module name.
   * This is based on module name comparison.
   */
  private def isConfigurationCompatibleWithModuleName(
    config: ConfigDetails,
    moduleName: String,
    prodTestSourcesSeparated: Boolean,
    isDowngradingFromSeparateMainTestModules: Option[Boolean]
  ): Boolean = {
    def cutSourceSetSuffix(str: String): String =
      "(\\.test|\\.main)$".r.replaceAllIn(str, "")

    if (config.isTest && prodTestSourcesSeparated && !moduleName.endsWith(".test")) return false

    val oldModuleName = config.oldModuleName
    // The main/test suffix is removed from the module name during grouping scheme migration or updates to main/test modules.
    // It's theoretically unnecessary when updating from old grouping to new grouping since there is no suffix to remove, but it doesn't cause incorrect results.
    // Examples:
    // - from old grouping to new grouping
    //    OldModuleName = foo
    //    After Reload = root.foo
    //      - `root.foo`.endsWith(`foo`) = true
    // - from old grouping to main/test modules
    //    OldModuleName = foo
    //    After Reload = root.foo.main
    //      - `main` from the `root.foo.main` is removed and `root.foo`.endsWith(`foo`) = true
    // - from new grouping to main/test modules
    //    OldModuleName = root.foo
    //    After Reload = root.foo.main
    //      - `main` from the `root.foo.main` is removed and `root.foo` == `root.foo` (`isUpgradeToProdTestConditionMet` works)
    lazy val isUpgradingConditionMet = {
      val trimmedModuleName = cutSourceSetSuffix(moduleName)
      val isGroupingConditionMet = trimmedModuleName.endsWith(s".$oldModuleName")
      val isUpgradeToProdTestConditionMet = prodTestSourcesSeparated && trimmedModuleName == oldModuleName

      isGroupingConditionMet || isUpgradeToProdTestConditionMet
    }

    // Example:
    //  Separate modules for prod/test enabled = roo.foo.main
    //  After Reload = root.foo
    //    - `main` from the `root.foo.main` is removed (removing the main/test suffix from the module name saved in the configuration) and `root.foo` == `root.foo`
    lazy val isDowngradeFromProdTestConditionMet =
      !prodTestSourcesSeparated && hasMainOrTestSuffix(oldModuleName) && moduleName == cutSourceSetSuffix(oldModuleName)

    isDowngradingFromSeparateMainTestModules match {
      case Some(isDowngrading) =>
        if (isDowngrading) isDowngradeFromProdTestConditionMet
        else isUpgradingConditionMet
      case _ =>
        isUpgradingConditionMet || isDowngradeFromProdTestConditionMet
    }
  }

  private def extractMainClassName(config: ModuleBasedConfiguration[?, ?]): Option[String] =
    config match {
      case x: ApplicationConfiguration => Option(x.getMainClassName)
      case x: ModuleBasedConfiguration[_, _] => ModuleBasedConfigurationDetailsExtractor.getMainClass(x)
    }

  private def extractJavaRunConfigurationModule(config: ModuleBasedConfiguration[?, ?]): Option[JavaRunConfigurationModule] =
    config.getConfigurationModule match {
      case x: JavaRunConfigurationModule => Option(x)
      case _ => None
    }
}
