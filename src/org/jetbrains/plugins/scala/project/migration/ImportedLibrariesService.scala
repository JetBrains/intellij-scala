package org.jetbrains.plugins.scala.project.migration

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.project.{LibraryData, LibraryDependencyData, LibraryPathType, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.vfs.JarFileSystem
import org.jetbrains.plugins.scala.codeInspection.bundled.BundledInspectionBase
import org.jetbrains.plugins.scala.components.libinjection.LibraryInjectorLoader
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, MigrationReport, SettingsDescriptor}
import org.jetbrains.plugins.scala.project.migration.apiimpl.MigrationApiImpl
import org.jetbrains.plugins.scala.project.migration.handlers.{ArtifactHandlerComponent, ScalaLibraryMigrationHandler}
import org.jetbrains.plugins.scala.project.migration.store.ManifestAttributes.ManifestAttribute
import org.jetbrains.plugins.scala.project.migration.store.{ManifestAttributes, SerializationUtil}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * User: Dmitry.Naydanov
  * Date: 25.07.16.
  */
class ImportedLibrariesService extends AbstractProjectDataService[LibraryDependencyData, LibraryOrderEntry] {
  private def isEnabled(project: Project) = ScalaProjectSettings.getInstance(project).isBundledMigratorsSearchEnabled
  
  override def getTargetDataKey: Key[LibraryDependencyData] = ProjectKeys.LIBRARY_DEPENDENCY

  override def importData(toImport: util.Collection[DataNode[LibraryDependencyData]], 
                          projectData: ProjectData, project: Project, 
                          modelsProvider: IdeModifiableModelsProvider): Unit = {
    if (!isEnabled(project)) return 
    
    val it = toImport.iterator()
    val libAcc = mutable.HashSet[LibraryData]()
    while (it.hasNext) libAcc += it.next().getData.getTarget
    
    BundledCodeStoreComponent.getInstance(project).setLibraryData(libAcc)
  }
}