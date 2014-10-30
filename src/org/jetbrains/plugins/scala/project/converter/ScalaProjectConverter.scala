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

  private val scalaFacets: Seq[ScalaFacetData] = scalaFacetsIn(context)
  private val obsoleteLibraries: Set[LibraryReference] = obsoleteLibrariesIn(context)

  private var createdSettingsFiles: Seq[File] = Seq.empty

  override def getAdditionalAffectedFiles =
    obsoleteLibraries.flatMap(_.libraryStorageFileIn(context)).asJava

  override def createModuleFileConverter(): ConversionProcessor[ModuleSettings] = scalaModuleConverter

  override def processingFinished() {
    updateScalaCompilerSettings()
    deleteObsoleteLibraries()
  }

  private def updateScalaCompilerSettings() {
    val compilerOptions = ScalaCompilerOptions.generalize(scalaFacets.map(_.compilerOptions))
    createdSettingsFiles = compilerOptions.createIn(context).toSeq
  }

  private def deleteObsoleteLibraries() {
    obsoleteLibraries.foreach(_.deleteIn(context))
  }

  override def getCreatedFiles = (scalaModuleConverter.createdFiles ++ createdSettingsFiles).asJava
}

private object ScalaProjectConverter {
  def findStandardScalaLibraryIn(module: ModuleSettings): Option[LibraryReference] =
    LibraryReference.findAllIn(module).find(_.name.contains("scala-library"))

  private def findScalaCompilerLibraryIn(module: ModuleSettings): Option[LibraryReference] =
    ScalaFacetData.findIn(module).flatMap(_.compilerLibrary)

  private def scalaFacetsIn(context: ConversionContext): Seq[ScalaFacetData] =
    modulesIn(context).flatMap(ScalaFacetData.findIn)

  private def modulesIn(context: ConversionContext): Seq[ModuleSettings] =
    context.getModuleFiles.map(context.getModuleSettings).toSeq

  private def obsoleteLibrariesIn(context: ConversionContext): Set[LibraryReference] = {
    val modules = modulesIn(context)

    val referencedLibraries = modules.flatMap(LibraryReference.findAllIn).toSet

    val standardLibraries = modules.flatMap(findStandardScalaLibraryIn).toSet
    val compilerLibraries = modules.flatMap(findScalaCompilerLibraryIn).toSet

    standardLibraries ++ (compilerLibraries -- referencedLibraries)
  }
}
