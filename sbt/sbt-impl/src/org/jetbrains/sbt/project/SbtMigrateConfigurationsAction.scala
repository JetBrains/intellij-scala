package org.jetbrains.sbt.project

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.{JavaRunConfigurationModule, ModuleBasedConfiguration, RunConfigurationModule}
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleManager, ModuleType, ModuleTypeManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiDocumentManager}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.{SbtBundle, SbtUtil}
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.{ModuleHeuristicResult, extractMainClassName, isConfigurationInvalid, logger}
import org.jetbrains.sbt.project.extensionPoints.ModuleBasedConfigurationMainClassExtractor

import scala.util.Try

class SbtMigrateConfigurationsAction extends AnAction(
  SbtBundle.message("sbt.migrate.configurations.text")
) {

  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    val place = e.getPlace
    // note: it is kind of hack, to have a different name for the action in the notification and a different name elsewhere.
    if (place == ActionPlaces.ACTION_SEARCH || place == ActionPlaces.MAIN_MENU) {
      presentation.setText(SbtBundle.message("sbt.migrate.configurations.full.title"))
    }
    val project = e.getProject
    if (project == null || !SbtUtil.isSbtProject(project)) {
      presentation.setEnabled(false)
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null || project.isDefault) return

    val moduleBasedConfigurations = SbtUtil.getAllModuleBasedConfigurationsInProject(project)
    val modules = classPathProviderModules(project)
    val configToHeuristicResult = for {
      config <- moduleBasedConfigurations
      configurationModule = config.getConfigurationModule
      oldModuleName = configurationModule.getModuleName
      if isConfigurationInvalid(config, configurationModule, oldModuleName)
    } yield config -> mapConfigurationToHeuristicResult(config, oldModuleName, modules, project)

    if (configToHeuristicResult.isEmpty) {
      Messages.showWarningDialog(project, SbtBundle.message("sbt.migrate.configurations.warning.message"), SbtBundle.message("sbt.migrate.configurations.warning.title"))
    } else {
      val dialogWrapper = new MigrateConfigurationsDialogWrapper(modules, configToHeuristicResult.toMap)
      val changedConfigToModule = dialogWrapper.open()
      changedConfigToModule.foreach { case (config, module) =>
        config.setModule(module)
        logger.info(s"In ${config.getName} configuration, the module was changed to ${module.getName}")
      }
    }
  }

  private def classPathProviderModules(project: Project): Array[Module] = {
    val modules = ModuleManager.getInstance(project).getModules
    val moduleTypeManager = ModuleTypeManager.getInstance()
    // note: it is written based on com.intellij.execution.ui.ModuleClasspathCombo.isModuleAccepted
    // I didn't use ModuleClasspathCombo directly in the org.jetbrains.sbt.project.MigrateConfigurationsDialogWrapper.myTable,
    // cause it will require additional non-obvious tricks to display it nicely it in a table cell.
    modules.filter(m => moduleTypeManager.isClasspathProvider(ModuleType.get(m)))
  }

  /**
   * @param modules include only classpath provider modules (it doesn't contain shared sources or build modules)
   */
  private def mapConfigurationToHeuristicResult(
    config: ModuleBasedConfiguration[_, _],
    oldModuleName: String,
    modules: Array[Module],
    project: Project
  ): ModuleHeuristicResult  = {
    // finding new modules that end with old module name e.g.
    // oldModuleName = foo and some module in modules could be root.foo
    val possibleModules = modules.filter(_.getName.endsWith(s".$oldModuleName")).toSeq
    val modulesForClass = getModulesInWhichMainClassExists(config, project)
    val combinedModules = (possibleModules ++ modulesForClass).distinct
    val productOfModuleSets = possibleModules.filter(modulesForClass.contains)
    productOfModuleSets match {
      case Seq(head) => ModuleHeuristicResult(Some(head))
      // note: it may happen that module from modulesForClass will be of SharedSourcesModuleType type.
      // Because of that we have to find their JVM representation.
      case Seq() if modulesForClass.size == 1 => ModuleHeuristicResult(modulesForClass.head.findJVMModule)
      // note: when there is more than 10 possible modules, displaying them in a tooltip can introduce additional chaos
      case Seq() if combinedModules.size < 10 =>
        val onlyJVMModules = combinedModules.flatMap(_.findJVMModule).map(_.getName)
        ModuleHeuristicResult(None, onlyJVMModules)
      case _ if productOfModuleSets.size < 10 => ModuleHeuristicResult(None, productOfModuleSets.map(_.getName))
      case _  =>
        if (combinedModules.size >= 10) {
          val combinedModulesString = combinedModules.map(_.getName).mkString(",")
          logger.warn(s"For ${config.getName} the upgrade configuration action found 10 or more modules: $combinedModulesString")
        }
        ModuleHeuristicResult(None)
    }
  }

  private def getModulesInWhichMainClassExists(config: ModuleBasedConfiguration[_, _], project: Project): Seq[Module] = {
    // note: some module that was previously called e.g. foo may be present in two builds e.g. root1 and root2.
    // In the new grouping such modules will be called root1.foo and root2.foo (or e.g. root1.group.foo, if module foo is grouped).
    // If such situation occurs (that we have more than one new module which may be the equivalent of an old module), then I first check in which of these
    // modules main class is available. It may happen that it will only be available in one module from the list of possible modules and
    // the situation will be solved because we will only have one module that fits.
    // If this does not happen, all possible modules are displayed as tooltip in MigrateConfigurationsDialogWrapper
    val mainClassName = extractMainClassName(config)
    if (mainClassName == null) Seq.empty
    else getModulesForClass(mainClassName, project)
  }

  /**
   * It's written based on [[com.intellij.execution.configurations.JavaRunConfigurationModule#getModulesForClass]].
   * I decided not to use it directly, because it also adds dependant modules to the result. In theory it is also possible
   * to run some configurations in dependant modules, but it doesn't seem to be common practice.
   */
  private def getModulesForClass(mainClassName: String, project: Project): Seq[Module] = {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val possibleClasses = JavaPsiFacade.getInstance(project).findClasses(mainClassName, GlobalSearchScope.projectScope(project)).toSeq
    possibleClasses.map(ModuleUtilCore.findModuleForPsiElement).filter(_ != null)
  }
}

object SbtMigrateConfigurationsAction {
  val ID = "Scala.Sbt.MigrateConfigurations"
  private val logger: Logger = Logger.getInstance(classOf[SbtMigrateConfigurationsAction])

  /**
   * @param module single suitable module for configuration identified using heuristic from [[org.jetbrains.sbt.project.SbtMigrateConfigurationsAction#mapConfigurationToHeuristicResult]]
   * @param guesses contains all possible modules (max 10), if heuristic in [[org.jetbrains.sbt.project.SbtMigrateConfigurationsAction#mapConfigurationToHeuristicResult]]
   *                won't be able to find a single suitable module for configuration
   */
  case class ModuleHeuristicResult(module: Option[Module], guesses: Seq[String] = Seq.empty)

  def isConfigurationInvalid(
    config: ModuleBasedConfiguration[_, _],
    configurationModule: RunConfigurationModule,
    oldModuleName: String
  ): Boolean = {
    /*
    1. If oldModuleName is non-empty and configurationModule.getModule is not null, it's still possible that the configuration may still be broken.
    Check #isMainClassInConfigurationModule ScalaDoc for more details.
    2. If configuration doesn't have a module, then moduleName is empty
    */
    oldModuleName.nonEmpty && (configurationModule.getModule == null || isMainClassAbsentInConfigurationModule(config))
  }

  /**
   * Checks whether a configuration module doesn't contain an expected main class.
   * If yes, the situation like that could happen - in the old grouping there may have been an IDEA module called X
   * owned by project Y (project in the sbt sense), and in the new grouping the same module (X) may belong to another project e.g. Z.
   * In that case, the configuration that had a module called X will still have it, but it will no longer be the same module as the original one and
   * some main class may no longer exists inside it.
   */
  private def isMainClassAbsentInConfigurationModule(config: ModuleBasedConfiguration[_, _]): Boolean = {
    val mainClassName = extractMainClassName(config)
    val javaRunConfigurationModule = extractJavaRunConfigurationModule(config)
    if (mainClassName != null && javaRunConfigurationModule != null) {
      Try(javaRunConfigurationModule.findNotNullClass(mainClassName)).toOption.isEmpty
    } else {
      false
    }
  }

  @Nullable
  def extractMainClassName(config: ModuleBasedConfiguration[_, _]): String =
    config match {
      case x: ApplicationConfiguration => x.getMainClassName
      case x: JUnitConfiguration => x.getPersistentData.getMainClassName
      case x: ModuleBasedConfiguration[_, _] => ModuleBasedConfigurationMainClassExtractor.getMainClass(x).orNull
      case _ => null
    }

  @Nullable
  private def extractJavaRunConfigurationModule(config: ModuleBasedConfiguration[_, _]): JavaRunConfigurationModule =
    config.getConfigurationModule match {
      case x: JavaRunConfigurationModule => x
      case _ => null
    }
}
