package org.jetbrains.sbt
package project

import java.util
import java.io.File
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import com.intellij.openapi.externalSystem.service.project.manage.{ModuleDataService, ProjectDataService}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModifiableRootModel, OrderRootType, ModuleRootManager}
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class BuildModuleDataService(platformFacade: PlatformFacade, delegate: ModuleDataService, helper: ProjectStructureHelper)
  extends ProjectDataService[BuildModuleData, Project] {

  def getTargetDataKey = BuildModuleData.Key

  def importData(toImport: util.Collection[DataNode[BuildModuleData]], project: Project, synchronous: Boolean) {
    delegate.importData(toImport.asInstanceOf[util.Collection[DataNode[ModuleData]]], project, synchronous)

    toImport.asScala.foreach { node =>
      val data = node.getData
      val module = helper.findIdeModule(data.name, project)

      val model = ModuleRootManager.getInstance(module).getModifiableModel

      BuildModuleDataService.updateContentDirectories(model, data)
      BuildModuleDataService.updateBuildClasspath(model, data.classpath)

      model.commit()
    }
  }

  def removeData(toRemove: util.Collection[_ <: Project], project: Project, synchronous: Boolean) {}
}

object BuildModuleDataService {
  private val LibraryName = "sbt-and-plugins"

  private def updateBuildClasspath(model: ModifiableRootModel, classpath: Seq[File]) {
    val libraryTable = model.getModuleLibraryTable

    Option(libraryTable.getLibraryByName(LibraryName))
      .foreach(libraryTable.removeLibrary(_))

    val library = libraryTable.createLibrary(LibraryName)
    val libraryModel = library.getModifiableModel

    classpath.foreach {
      entry =>
        libraryModel.addRoot(entry.url, OrderRootType.CLASSES)
    }

    libraryModel.commit()
  }

  private def updateContentDirectories(model: ModifiableRootModel, data: BuildModuleData) {
    val root = new File(data.path)

    val contentEntry = model.addContentEntry(root.url)
    contentEntry.clearSourceFolders()
    data.sourceDirs.foreach(dir => contentEntry.addSourceFolder(dir.url, false))

    contentEntry.clearExcludeFolders()
    data.excludedDirs.foreach(dir => contentEntry.addExcludeFolder(dir.url))
  }
}
