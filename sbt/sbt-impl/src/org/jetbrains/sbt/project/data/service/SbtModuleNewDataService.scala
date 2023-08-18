package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemConstants, Order}
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.util.Computable
import com.intellij.util.SmartList
import org.jetbrains.plugins.scala.project.external.IdeModifiableModelsProviderImplicitConversions
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.{Sbt, SbtUtil}

import java.util
import scala.collection.immutable.Seq
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Order(ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER + 10)
class SbtModuleNewDataService extends AbstractModuleDataService[ModuleData] with IdeModifiableModelsProviderImplicitConversions {

  override def getTargetDataKey: Key[ModuleData] = ProjectKeys.MODULE
      override def computeOrphanData(toImport: util.Collection[_ <: DataNode[ModuleData]], projectData: ProjectData, project: Project, modelsProvider: IdeModifiableModelsProvider): Computable[util.Collection[Module]] = {
        () => {
          val orphanIdeModules = new SmartList[Module]
          modelsProvider.getModules
            .filterNot(_.isDisposed)
            .filter(ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.getOwner, _))
            .filter(ExternalSystemApiUtil.getExternalModuleType(_) == null)
            .foreach { module =>
              val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
              if (projectData.getLinkedExternalProjectPath == rootProjectPath && module.getUserData(AbstractModuleDataService.MODULE_DATA_KEY) == null)
                orphanIdeModules.add(module)
            }
          orphanIdeModules
        }
      }

  override def importData(toImport: util.Collection[_ <: DataNode[ModuleData]],
                          projectData: ProjectData,
                          project: Project,
                          modelsProvider: IdeModifiableModelsProvider): Unit = {

    val modules = toImport.asScala.flatMap { node =>
      for {
        moduleData <- Option(node.getData(ProjectKeys.MODULE))
        if moduleData.getOwner == SbtProjectSystem.Id
        module <- modelsProvider.findIdeModuleOpt(moduleData)
      } yield (moduleData, module)
    }.toSeq
    val modulesGroupByRoot = modules.groupBy { case (moduleData, _) => isRootModule(moduleData) }
    val roots = modulesGroupByRoot.getOrElse(true, Seq.empty).map(_._2.getName)

    def generateNewModuleName(moduleDataId: String, moduleName: String, rootModuleName: String, alreadyReservedNames: Seq[String], pathUsedToPrefixModuleName: String) = {
      val newModuleNameProposalOpt = SbtUtil.getBuildModuleData(project, moduleDataId) match {
        case Some(_) => generateModuleNameForBuildModule(moduleName, rootModuleName)
        case None => Some(s"$rootModuleName.$moduleName")
      }
      newModuleNameProposalOpt
        .flatMap { moduleNameProposal =>
          if (alreadyReservedNames.contains(moduleNameProposal)) {
            generateAlternativeModuleName(alreadyReservedNames, rootModuleName, moduleNameProposal, pathUsedToPrefixModuleName, moduleName)
          } else {
            newModuleNameProposalOpt
          }
      }
    }

    val result = modulesGroupByRoot.getOrElse(false, Seq.empty)
      .foldLeft(Seq.empty[(Boolean, String, Module)]) { case (acc, (moduleData, module)) =>

        val rootProjectPath = moduleData.getProperty(Sbt.moduleDataKeyForRootModulePath)
        val rootForModule = modules.find { case (md, _) => rootProjectPath == md.getLinkedExternalProjectPath }
        val alreadyReservedNames = (acc.map(_._3.getName) ++ roots ++ modelsProvider.getModules.map(_.getName)).distinct

        val newModuleNameOpt = for {
          (rootModuleData, rootModule) <- rootForModule
//          rootModuleName = extractRootModuleName(Option(rootModuleData.getGroup), rootModule.getName)
          pathUsedToPrefixModuleName = moduleData.getLinkedExternalProjectPath.stripPrefix(rootModuleData.getLinkedExternalProjectPath)
          rootModuleName = rootModule.getName
          moduleName = moduleData.getInternalName
          moduleNameProposal <- generateNewModuleName(moduleData.getId, moduleName, rootModuleName, alreadyReservedNames, pathUsedToPrefixModuleName)
          if moduleNameProposal != module.getName
        } yield {
          val existingModule = modelsProvider.getModules.exists(_.getName == moduleNameProposal)
          val existingModuleWithModuleManager = ModuleManager.getInstance(project).getModules.find(_.getName == moduleNameProposal)
          modelsProvider.getModifiableModuleModel.renameModule(module, moduleNameProposal)

          (existingModule, moduleNameProposal, module)
        }

        newModuleNameOpt match {
          case Some(moduleName) => acc :+ moduleName
          case _ => acc
        }
      }

//    val sorted = result.sortBy(_._1)
//    sorted.foreach { case (_, newModuleName, module) =>
//      modelsProvider.getModifiableModuleModel.renameModule(module, newModuleName)
//    }
  }

  private def extractRootModuleName(groupName: Option[String], rootModuleName: String): String =
    groupName.map { name =>
      val suffixIndex = rootModuleName.indexOf(name)
      if (suffixIndex != -1) {
        rootModuleName.substring(0, suffixIndex - 1)
      } else rootModuleName
    }.getOrElse(rootModuleName)

  private def isRootModule(moduleData: ModuleData): Boolean =
    Option(moduleData.getProperty(Sbt.moduleDataKeyForRootModulePath)).exists(_.contains(moduleData.getLinkedExternalProjectPath))

  private def generateModuleNameForBuildModule(moduleName: String, rootModuleName: String): Option[String] = {
    val currentModuleNameInside = moduleName.stripPrefix(Sbt.MultipleBuildModulePrefix).stripSuffix(Sbt.BuildModuleSuffix)
    if (currentModuleNameInside != rootModuleName) {
      if (moduleName.startsWith(Sbt.MultipleBuildModulePrefix)) Some(s"${Sbt.MultipleBuildModulePrefix}$rootModuleName${Sbt.BuildModuleSuffix}")
      else Some(s"$rootModuleName${Sbt.BuildModuleSuffix}")
    }
    else None
  }

  private def generateAlternativeModuleName(alreadyUsedNames: Seq[String], rootModuleName: String, moduleNameProposal: String, path: String, moduleName: String): Option[String] =
    SbtUtil.generatePossibleName(moduleName, path, s"$rootModuleName.").diff(alreadyUsedNames).headOption.orElse {
      val possibleNamesWithNumberSuffix = Seq.fill(2)(moduleNameProposal).zipWithIndex.map { case (name, number) => s"$name~$number" }
      possibleNamesWithNumberSuffix.diff(alreadyUsedNames).headOption
    }

}
