package org.jetbrains.sbt.project

import com.intellij.execution.RunManager
import com.intellij.execution.application.{ApplicationConfiguration, ApplicationConfigurationType}
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.{ModuleHeuristicResult, logger}

import scala.jdk.CollectionConverters.{CollectionHasAsScala, ListHasAsScala}

class SbtMigrateConfigurationsAction extends AnAction {

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null) return
    val runManager = RunManager.getInstance(project)
    val applicationConfigurations = runManager.getConfigurationsList(ApplicationConfigurationType.getInstance())
      .asScala.toSeq.asInstanceOf[Seq[ApplicationConfiguration]]

    val modules = ModuleManager.getInstance(project).getModules
    val configToHeuristicResult = for {
      config <- applicationConfigurations
      configurationModule = config.getConfigurationModule
      name = configurationModule.getModuleName
      if name.nonEmpty && configurationModule.getModule == null
    } yield config -> mapApplicationConfigurationToHeuristicResult(config, modules, project)

    if (configToHeuristicResult.isEmpty) {
      Messages.showWarningDialog(project, SbtBundle.message("sbt.migrate.configurations.warning.message"), SbtBundle.message("sbt.migrate.configurations.warning.title"))
    } else {
      val dialogWrapper = new MigrateConfigurationsDialogWrapper(project, configToHeuristicResult.toMap)
      val changedConfigToModule = dialogWrapper.open()
      changedConfigToModule.collect { case(config, Some(module)) =>
        config.setModule(module)
        logger.info(s"In configuration ${config.getName}, the module was changes to ${module.getName}")
      }
    }
  }

  private def mapApplicationConfigurationToHeuristicResult(
    config: ApplicationConfiguration,
    modules: Array[Module],
    project: Project
  ): ModuleHeuristicResult  = {
    val oldModuleName = config.getConfigurationModule.getModuleName
    // finding new modules that end with old module name
    val potentialNewModules = modules.filter(_.getName.endsWith(s".$oldModuleName")).toSeq
    potentialNewModules match {
      case head :: Nil => ModuleHeuristicResult(Some(head))
      case Nil => ModuleHeuristicResult(None)
      case _ =>
        // note: some module that was previously called e.g. foo may be present in two builds e.g. root1 and root2.
        // In the new grouping such modules will be called have root1.foo and root2.foo (or e.g. root1.group.foo, if module foo is grouped).
        // If such situation occurs (that we have more than one new module which may be the equivalent of an old module), then I first check in which of these
        // modules this class is available at all. It may happen that it will only be available in one module from the list of "potential" modules and
        // the situation will be resolved because we will only have one module that fits.
        // If this does not happen, all potential modules are displayed as tooltip in MigrateConfigurationsDialogWrapper
        val mainClass = config.getMainClassName
        val modulesForClass = JavaRunConfigurationModule.getModulesForClass(project, mainClass).asScala.toSeq
        val productOfModuleSets = modulesForClass.filter(potentialNewModules.contains)
        productOfModuleSets match {
          case head :: Nil => ModuleHeuristicResult(Some(head))
          case Nil => ModuleHeuristicResult(None, potentialNewModules.map(_.getName))
          case _  => ModuleHeuristicResult(None, productOfModuleSets.map(_.getName))
        }
    }
  }
}

object SbtMigrateConfigurationsAction {
  val logger: Logger = Logger.getInstance(classOf[SbtMigrateConfigurationsAction])

  case class ModuleHeuristicResult(module: Option[Module], guesses: Seq[String] = Seq.empty)
}
