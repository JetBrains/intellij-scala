package org.jetbrains.sbt.project

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.{ModuleBasedConfiguration, RunConfigurationBase}
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiDocumentManager}
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.{SbtBundle, SbtUtil}
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.{ModuleHeuristicResult, logger}
import org.jetbrains.sbt.project.extensionPoints.ModuleBasedConfigurationMainClassExtractor

class SbtMigrateConfigurationsAction extends AnAction {

  override def update(e: AnActionEvent): Unit = {
    // note: it is kind of hack, to have a different name for the action in the notification and a different name elsewhere.
    val place = e.getPlace
    if (place == ActionPlaces.ACTION_SEARCH || place == ActionPlaces.MAIN_MENU) {
      e.getPresentation.setText(SbtBundle.message("sbt.migrate.configurations.full.title"))
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null) return

    val moduleBasedConfigurations = SbtUtil.getAllModuleBasedConfigurationsInProject(project)
    val modules = ModuleManager.getInstance(project).getModules
    val configToHeuristicResult = for {
      config <- moduleBasedConfigurations
      configurationModule = config.getConfigurationModule
      oldModuleName = configurationModule.getModuleName
      if oldModuleName.nonEmpty && configurationModule.getModule == null
    } yield config -> mapConfigurationToHeuristicResult(config, oldModuleName, modules, project)

    if (configToHeuristicResult.isEmpty) {
      Messages.showWarningDialog(project, SbtBundle.message("sbt.migrate.configurations.warning.message"), SbtBundle.message("sbt.migrate.configurations.warning.title"))
    } else {
      val dialogWrapper = new MigrateConfigurationsDialogWrapper(project, configToHeuristicResult.toMap)
      val changedConfigToModule = dialogWrapper.open()
      changedConfigToModule.collect { case(config, Some(module)) =>
        config.setModule(module)
        logger.info(s"In ${config.getName} configuration, the module was changed to ${module.getName}")
      }
    }
  }

  private def mapConfigurationToHeuristicResult[T <: RunConfigurationBase[_]](
    config: ModuleBasedConfiguration[_, _],
    oldModuleName: String,
    modules: Array[Module],
    project: Project
  ): ModuleHeuristicResult  = {
    // finding new modules that end with old module name
    val possibleModules = modules.filter(_.getName.endsWith(s".$oldModuleName")).toSeq
    val modulesForClass = getModulesInWhichMainClassExists(config, project)
    val combinedModules = (possibleModules ++ modulesForClass).distinct
    val productOfModuleSets = possibleModules.filter(modulesForClass.contains)
    productOfModuleSets match {
      case Seq(head) => ModuleHeuristicResult(Some(head))
      case Seq() if modulesForClass.size == 1 => ModuleHeuristicResult(Some(modulesForClass.head))
      // note: when there is more than 10 possible modules, displaying them in a tooltip can introduce additional chaos
      case Seq() if combinedModules.size < 10 => ModuleHeuristicResult(None, combinedModules.map(_.getName))
      case _ if productOfModuleSets.size < 10 => ModuleHeuristicResult(None, productOfModuleSets.map(_.getName))
      case _  => ModuleHeuristicResult(None)
    }
  }

  private def getModulesInWhichMainClassExists(config: ModuleBasedConfiguration[_, _], project: Project): Seq[Module] = {
    // note: some module that was previously called e.g. foo may be present in two builds e.g. root1 and root2.
    // In the new grouping such modules will be called root1.foo and root2.foo (or e.g. root1.group.foo, if module foo is grouped).
    // If such situation occurs (that we have more than one new module which may be the equivalent of an old module), then I first check in which of these
    // modules main class is available. It may happen that it will only be available in one module from the list of possible modules and
    // the situation will be solved because we will only have one module that fits.
    // If this does not happen, all possible modules are displayed as tooltip in MigrateConfigurationsDialogWrapper
    val mainClassName = config match {
      case x: ApplicationConfiguration => x.getMainClassName
      case x: JUnitConfiguration => x.getPersistentData.getMainClassName
      // note: in this pattern match AbstractTestRunConfiguration in which testConfigurationData is ClassTestData could be handled.
      // I didn't implement it, because using AbstractTestRunConfiguration in sbtImpl module requires major changes in module structure.
      case x: ModuleBasedConfiguration[_, _] =>
        ModuleBasedConfigurationMainClassExtractor.getMainClassFromTestConfiguration(x).orNull
      case _ => null
    }
    getModulesForClass(mainClassName, project)
  }

  // note: this method is based on com.intellij.execution.configurations.JavaRunConfigurationModule.getModulesForClass.
  // I decided not to use it, because it also adds dependant modules to the result. In theory it is also possible
  // to run some configurations in dependant modules, but it doesn't seem to be common practice.
  private def getModulesForClass(@Nullable mainClassName: String, project: Project): Seq[Module] = {
    if (project.isDefault || mainClassName == null) return Seq.empty
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val possibleClasses = JavaPsiFacade.getInstance(project).findClasses(mainClassName, GlobalSearchScope.projectScope(project))

    possibleClasses.foldLeft(Seq.empty[Module]) { case(acc, psiClass) =>
      val module = ModuleUtilCore.findModuleForPsiElement(psiClass)
      if (module != null) acc :+ module
      else acc
    }
  }
}

object SbtMigrateConfigurationsAction {
  val ID = "Scala.Sbt.MigrateConfigurations"
  val logger: Logger = Logger.getInstance(classOf[SbtMigrateConfigurationsAction])

  case class ModuleHeuristicResult(module: Option[Module], guesses: Seq[String] = Seq.empty)
}
