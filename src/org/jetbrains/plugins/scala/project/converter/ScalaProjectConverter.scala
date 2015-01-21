package org.jetbrains.plugins.scala
package project.converter

import java.io.File

import com.intellij.conversion._
import collection.JavaConverters._
import ScalaProjectConverter._

/**
 * @author Pavel Fatin
 */
class ScalaProjectConverter(context: ConversionContext) extends ProjectConverter {
  private val scalaModuleConverter = new ScalaModuleConversionProcessor(context)

  private val scalaCompilerSettings: Map[String, ScalaCompilerSettings] = scalaCompilerSettingsIn(context)
  private val obsoleteProjectLibraries: Set[LibraryReference] = obsoleteLibrariesIn(context).filter(_.level == ProjectLevel)

  private var createdSettingsFiles: Seq[File] = Seq.empty

  override def getAdditionalAffectedFiles =
    obsoleteProjectLibraries.flatMap(_.libraryStorageFileIn(context)).asJava

  override def createModuleFileConverter(): ConversionProcessor[ModuleSettings] = scalaModuleConverter

  override def processingFinished() {
    updateScalaCompilerSettings()
    deleteObsoleteProjectLibraries()
  }

  private def updateScalaCompilerSettings() {
    val compilerConfiguration = merge(scalaCompilerSettings)
    createdSettingsFiles = compilerConfiguration.createIn(context).toSeq
  }

  private def deleteObsoleteProjectLibraries() {
    obsoleteProjectLibraries.foreach(_.deleteIn(context))
  }

  override def getCreatedFiles = (scalaModuleConverter.createdFiles ++ createdSettingsFiles).asJava
}

private object ScalaProjectConverter {
  def findStandardScalaLibraryIn(module: ModuleSettings): Option[LibraryReference] =
    LibraryReference.findAllIn(module).find(_.name.contains("scala-library"))

  private def findScalaCompilerLibraryIn(module: ModuleSettings): Option[LibraryReference] =
    ScalaFacetData.findIn(module).flatMap(_.compilerLibrary)

  private def scalaCompilerSettingsIn(context: ConversionContext): Map[String, ScalaCompilerSettings] =
    modulesIn(context).flatMap(module => ScalaFacetData.findIn(module).toSeq
            .map(facet => (module.getModuleName, facet.compilerSettings)).toSeq).toMap

  private def modulesIn(context: ConversionContext): Seq[ModuleSettings] =
    context.getModuleFiles.map(context.getModuleSettings).toSeq

  private def obsoleteLibrariesIn(context: ConversionContext): Set[LibraryReference] = {
    val modules = modulesIn(context)

    val referencedLibraries = modules.flatMap(LibraryReference.findAllIn).toSet

    val standardLibraries = modules.flatMap(findStandardScalaLibraryIn).toSet
    val compilerLibraries = modules.flatMap(findScalaCompilerLibraryIn).toSet

    standardLibraries ++ (compilerLibraries -- referencedLibraries)
  }

  private def merge(moduleSettings: Map[String, ScalaCompilerSettings]): ScalaCompilerConfiguration = {
    val settingsToModules = moduleSettings.groupBy(_._2).mapValues(_.keys.toSet).toSeq

    val sortedSettingsToModules = settingsToModules.sortBy(p => (p._2.size, p._1.isDefault)).reverse

    val profiles = sortedSettingsToModules.zipWithIndex.map {
      case ((settings, modules), i) => new ScalaCompilerSettingsProfile("Profile " + i, modules.toSeq, settings)
    }

    val defaultSettings = profiles.headOption.fold(ScalaCompilerSettings.Default)(_.settings)
    val customProfiles = profiles.drop(1)

    new ScalaCompilerConfiguration(defaultSettings, customProfiles)
  }
}
