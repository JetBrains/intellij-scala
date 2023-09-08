package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.{ExternalSystemSourceType, ModuleData, ProjectCoordinate, ProjectData}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemConstants, Order}
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.{CompilerModuleExtension, ModifiableRootModel, ModuleRootManager, TestModuleProperties}
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.util.SmartList
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{BooleanExt, inWriteAction, invokeAndWait, invokeLater}
import org.jetbrains.plugins.scala.util.ExternalSystemUtil
import org.jetbrains.sbt.{Sbt, SbtUtil}
import org.jetbrains.sbt.project.SbtProjectSystem

import java.util
import java.util.Arrays
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Order(ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER + 10)
class SbtModuleNewDataService extends AbstractModuleDataService[ModuleData] {

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

  //private val EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.moduleDataServiceExtension")
//  override def importData(toImport: util.Collection[_ <: DataNode[ModuleData]],
//                          projectData: ProjectData,
//                          project: Project,
//                          modelsProvider: IdeModifiableModelsProvider): Unit = {
//
//    val modules = toImport.asScala.flatMap { dataNode => modelsProvider.getIdeModuleByNode(dataNode) }.toSeq
//    modules.foreach { case (_, moduleData, module) =>
//      val isRoot = moduleData.getProperty(Sbt.moduleDataKeyForProjectURI).contains(moduleData.getLinkedExternalProjectPath)
//      if (!isRoot) {
//        val myURI = moduleData.getProperty(Sbt.moduleDataKeyForProjectURI)
//        val rootForModule = modules.find { case (_, md, _) => myURI == md.getLinkedExternalProjectPath }
//        val oldName = module.getName
//
//        val oldNameWithRemovedDummy = if (moduleData.getGroup != null && moduleData.getGroup.nonEmpty) {
//          oldName.substring(oldName.indexOf(moduleData.getGroup))
//        } else {
//          oldName.substring(oldName.indexOf(moduleData.getModuleName))
//        }
//        rootForModule.foreach { case (_, mdRoot, mRoot) =>
//          val isBuildModule = SbtUtil.getBuildModuleData(project, moduleData.getId)
//          val groupSuffix = Option(mdRoot.getGroup).map(group => s".$group").getOrElse("")
//          val rootName = mRoot.getName.stripSuffix(groupSuffix)
//          val newModuleName = if (isBuildModule.nonEmpty) {
//            val currInside = moduleData.getInternalName.stripPrefix("sbt-build-modules.").stripSuffix("-build")
//            if (currInside != rootName) {
//              s"sbt-build-modules.$rootName-build"
//            } else ""
//          } else {
//            s"$rootName.$oldNameWithRemovedDummy"
//          }
//          //check if module exists
//          if (newModuleName != oldName) {
//            val existingModule = modelsProvider.getModules.find(_.getName == newModuleName)
//            val existingModuleWithModuleManager = ModuleManager.getInstance(project).getModules.find(_.getName == newModuleName)
//            removeIfModuleExists(project, newModuleName)
//            modelsProvider.getModifiableModuleModel.renameModule(module, newModuleName)
//          }
//
//          }
//        }
//      }
//    }

//  override def importData(toImport: util.Collection[_ <: DataNode[ModuleData]],
//                          projectData: ProjectData,
//                          project: Project,
//                          modelsProvider: IdeModifiableModelsProvider): Unit = {
//
//    def isRootModule(moduleData: ModuleData): Boolean = {
//      val myURI = moduleData.getProperty(Sbt.moduleDataKeyForProjectURI)
//      myURI.contains(moduleData.getLinkedExternalProjectPath)
//    }
//
//
//    val modules = toImport.asScala.flatMap { dataNode => modelsProvider.getIdeModuleByNode(dataNode) }.toSeq
//    val roots = modules.filter { case (_, moduleData, _) => isRootModule(moduleData) }.map(_._3.getName)
//    val result = modules.filterNot { case (dataNode, moduleData, _) => isRootModule(moduleData) }
//      .foldLeft(Seq.empty[(Boolean, String, Module)]) { case (acc, (dataNode, moduleData, module)) =>
//        val myURI = moduleData.getProperty(Sbt.moduleDataKeyForProjectURI)
//        val rootForModule = modules.find { case (_, md, _) => myURI == md.getLinkedExternalProjectPath }
//        //val placeToCutName = Option(moduleData.getGroup).filter(_.nonEmpty).getOrElse(moduleData.get)
//        val moduleName = cutModuleName(moduleData.getInternalName, module.getName)
//
//        val newModuleNameOpt = rootForModule.flatMap { case (_, rootModuleData, rootModule) =>
//          val isBuildModule = SbtUtil.getBuildModuleData(project, moduleData.getId)
//          val rootName = Option(rootModuleData.getGroup).map(cutModuleName(_, rootModule.getName)).getOrElse(rootModule.getName)
//          val newModuleName = if (isBuildModule.nonEmpty) {
//            val currInside = moduleData.getInternalName.stripPrefix("sbt-build-modules.").stripSuffix("-build")
//            if (currInside != rootName) {
//              s"sbt-build-modules.$rootName-build"
//            } else ""
//          } else {
//            s"$rootName.$moduleName"
//          }
//          val alreadyCreatedModules = modelsProvider.getModules.filterNot(_ == module).exists(_.getName == newModuleName)
//          if (acc.map(_._2).contains(newModuleName) || roots.contains(newModuleName)) {
//            val rootPath = rootModuleData.getLinkedExternalProjectPath
//            val modulePath = moduleData.getLinkedExternalProjectPath
//            val possibleNames = SbtUtil.generatePossibleName(moduleName, modulePath.stripPrefix(rootPath), s"$rootName.")
//            val found = possibleNames.diff(acc).headOption.orElse {
//              val name = if (possibleNames.nonEmpty) possibleNames.last
//              else newModuleName
//              val kk = Seq.fill(2)(name).zipWithIndex.map { case (name, number) => s"$name~$number" }
//              kk.diff(acc).headOption
//            }
//            found
//          } else {
//            Option(newModuleName)
//          }
//        }.filter(_ != module.getName)
//
//        // check if module exists
//        //        val o = newModuleNameOpt.map { name =>
//        //                        moduleData.setInternalName(name)
//        //                        val dd = dataNode.asInstanceOf[DataNode[ModuleData]]
//        //                        val newModule = createModule(dd, modelsProvider)
//        //                        dd.putUserData(AbstractModuleDataService.MODULE_KEY, newModule)
//        //          module.putUserData(AbstractModuleDataService.MODULE_DATA_KEY, null)
//        ////          dataNode
//        //          val existingModule = modelsProvider.getModules.exists(_.getName == name)
//        ////          val found = modelsProvider.getModules.find(_.getName == name)
//        //          (existingModule.toInt, name, module)
//        //          //modelsProvider.getModifiableModuleModel.renameModule(module, name)
//        //        }
//
//        val o = newModuleNameOpt.map { name =>
//          val existingModule = modelsProvider.getModules.exists(_.getName == name)
//          val existingModuleWithModuleManager = ModuleManager.getInstance(project).getModules.find(_.getName == name)
//          //removeIfModuleExists(project, name, modelsProvider)
//          (existingModule, name, module)
//          // modelsProvider.getModifiableModuleModel.renameModule(module, name)
//        }
//        if (o.nonEmpty) {
//          acc :+ o.get
//        } else acc
//      }
//    val sorted = result.sortBy(_._1)
//    sorted.foreach { case (i, n, m) =>
//      modelsProvider.getModifiableModuleModel.renameModule(m, n)
//    }
//  }

  override def importData(toImport: util.Collection[_ <: DataNode[ModuleData]],
                          projectData: ProjectData,
                          project: Project,
                          modelsProvider: IdeModifiableModelsProvider): Unit = {

    def isRootModule(moduleData: ModuleData): Boolean = {
      val myURI = moduleData.getProperty(Sbt.moduleDataKeyForProjectURI)
      myURI.contains(moduleData.getLinkedExternalProjectPath)
    }

    def generateNewModuleNameProposal(moduleDataId: String, moduleName: String, rootModuleName: String) =
      SbtUtil.getBuildModuleData(project, moduleDataId) match {
        case Some(_) =>
          val currentModuleNameInside = moduleName.stripPrefix(Sbt.MultipleBuildModulePrefix).stripSuffix(Sbt.BuildModuleSuffix)
          if (currentModuleNameInside != rootModuleName) {
            if (moduleName.startsWith(Sbt.MultipleBuildModulePrefix)) Some(s"${Sbt.MultipleBuildModulePrefix}$rootModuleName${Sbt.BuildModuleSuffix}")
            else Some(s"$rootModuleName${Sbt.BuildModuleSuffix}")
          }
          else None
        case None => Some(s"$rootModuleName.$moduleName")
      }

    def generateAlternativeModuleName(alreadyUsedNames: Seq[String], rootModuleName: String, moduleNameProposal: String, path: String, moduleName: String): Option[String] = {
      val possibleNames = SbtUtil.generatePossibleName(moduleName, path, s"$rootModuleName.")
      val found = possibleNames.diff(alreadyUsedNames).headOption.orElse {
        val possibleNamesWithNumberSuffix = Seq.fill(2)(moduleNameProposal).zipWithIndex.map { case (name, number) => s"$name~$number" }
        possibleNamesWithNumberSuffix.diff(alreadyUsedNames).headOption
      }
      found
    }

    val modules = toImport.asScala.flatMap { dataNode => modelsProvider.getIdeModuleByNode(dataNode) }.toSeq
    val modulesByRoot = modules.groupBy{ case (_, moduleData, _) => isRootModule(moduleData) }
    val roots = modulesByRoot.getOrElse(true, Seq.empty).map(_._3.getName)

    val result = modulesByRoot.getOrElse(false, Seq.empty)
      .foldLeft(Seq.empty[(Boolean, String, Module)]) { case (acc, (_, moduleData, module)) =>

        val myURI = moduleData.getProperty(Sbt.moduleDataKeyForProjectURI)
        val rootForModule = modules.find { case (_, md, _) => myURI == md.getLinkedExternalProjectPath }

        val newModuleNameOpt = rootForModule.flatMap { case (_, rootModuleData, rootModule) =>
          val rootModuleName = extractRootModuleName(Option(rootModuleData.getGroup), rootModule.getName)
          val moduleName = moduleData.getInternalName
          val alreadyUsedNames = acc.map(_._3.getName)
          val moduleNameProposalOpt = generateNewModuleNameProposal(moduleData.getId, moduleName, rootModuleName)
            .flatMap { moduleNameProposal =>
              if (alreadyUsedNames.contains(moduleNameProposal) || roots.contains(moduleNameProposal)) {
                val pathUsedToPrefixModuleName = moduleData.getLinkedExternalProjectPath.stripPrefix(rootModuleData.getLinkedExternalProjectPath)
                generateAlternativeModuleName(alreadyUsedNames, rootModuleName, moduleNameProposal, pathUsedToPrefixModuleName, moduleName)
              } else {
                Option(moduleNameProposal)
              }
            }
            .filter(_ != module.getName)
            .map { name =>
              val existingModule = modelsProvider.getModules.exists(_.getName == name)
              val existingModuleWithModuleManager = ModuleManager.getInstance(project).getModules.find(_.getName == name)
              //removeIfModuleExists(project, name, modelsProvider)
              (existingModule, name, module)
              //modelsProvider.getModifiableModuleModel.renameModule(module, name)
            }
          moduleNameProposalOpt
        }

        if (newModuleNameOpt.nonEmpty) {
          acc :+ newModuleNameOpt.get
        } else acc

      }
      val sorted = result.sortBy(_._1)
      sorted.foreach { case (_, newModuleName, module) =>
        modelsProvider.getModifiableModuleModel.renameModule(module, newModuleName)
      }
  }

  private def extractRootModuleName(groupName: Option[String], rootModuleName: String): String =
    groupName.map { name =>
      val suffixIndex = rootModuleName.indexOf(name)
      if (suffixIndex != -1) {
        rootModuleName.substring(0, suffixIndex - 1)
      } else rootModuleName
    }.getOrElse(rootModuleName)

  private def removeIfModuleExists(project: Project, moduleName: String, modelsProvider: IdeModifiableModelsProvider) = {
    val moduleAlreadyExists = ModuleManager.getInstance(project).getModules.find(_.getName == moduleName)
    val name = modelsProvider.getModules.find(_.getName == moduleName)

    invokeAndWait {
      inWriteAction {
        moduleAlreadyExists.foreach(ModuleManager.getInstance(project).disposeModule(_))
        name.foreach(modelsProvider.getModifiableModuleModel.disposeModule(_))
      }
    }
  }

  protected implicit class IdeModifiableModelsProviderOps(private val modelsProvider: IdeModifiableModelsProvider) {

    def findIdeModuleOpt(name: String): Option[Module] =
      Option(modelsProvider.findIdeModule(name))

    def findIdeModuleOpt(data: ModuleData): Option[Module] =
      Option(modelsProvider.findIdeModule(data))

    def getIdeModuleByNode(node: DataNode[_]): Option[(DataNode[_], ModuleData, Module)] =
      for {
        moduleData <- Option(node.getData(ProjectKeys.MODULE))
        module <- findIdeModuleOpt(moduleData)
      } yield (node, moduleData, module)
  }

}
