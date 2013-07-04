package org.jetbrains.sbt
package project

import java.util
import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import collection.JavaConverters._
import com.intellij.openapi.module.Module

/**
 * @author Pavel Fatin
 */
class ModuleLibraryDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[ModuleLibraryData, Module](ModuleLibraryData.Key) {

  def doImportData(toImport: util.Collection[DataNode[ModuleLibraryData]], project: Project) {
    toImport.asScala.foreach { node =>
      val moduleData: ModuleData = node.getData(ProjectKeys.MODULE)
      val module = helper.findIdeModule(moduleData.getName, project)
      ModuleLibraryDataService.importLibrary(module, node.getData)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Module], project: Project) {
    toRemove.asScala.foreach(ModuleLibraryDataService.removeLibrary(_))
  }
}

object ModuleLibraryDataService {
  private def importLibrary(module: Module, data: ModuleLibraryData) {
    val model = ModuleRootManager.getInstance(module).getModifiableModel

    val libraryTable = model.getModuleLibraryTable

    Option(libraryTable.getLibraryByName(data.name))
      .foreach(libraryTable.removeLibrary(_))

    val library = libraryTable.createLibrary(data.name)
    val libraryModel = library.getModifiableModel

    data.classes.foreach { it =>
      libraryModel.addRoot(it.url, OrderRootType.CLASSES)
    }

    libraryModel.commit()

    model.commit()
  }

  private def removeLibrary(module: Module) {
    val model = ModuleRootManager.getInstance(module).getModifiableModel

    val libraryTable = model.getModuleLibraryTable

    Option(libraryTable.getLibraryByName(Sbt.BuildLibraryName)) // TODO don't hardcode the name
      .foreach(libraryTable.removeLibrary(_))

    model.commit()
  }
}
