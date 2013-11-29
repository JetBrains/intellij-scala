package org.jetbrains.plugins.scala
package converter

import com.intellij.conversion._
import collection.JavaConverters._
import ScalaProjectConverter._

/**
 * @author Pavel Fatin
 */
class ScalaProjectConverter(context: ConversionContext) extends ProjectConverter {
  private val scalaModuleConverter = new ScalaModuleConversionProcessor(context)

  private val scalaFacets: Seq[ScalaFacetData] = scalaFacetsIn(context)
  private val scalaProjectLibraries: Seq[LibraryReference] = scalaProjectLibrariesIn(context)

  override def getAdditionalAffectedFiles =
    scalaProjectLibraries.flatMap(_.libraryStorageFileIn(context)).asJava

  override def createModuleFileConverter(): ConversionProcessor[ModuleSettings] = scalaModuleConverter

  override def processingFinished() {
    updateScalaCompilerSettings()
    deleteScalaProjectLibraries()
  }

  private def updateScalaCompilerSettings() {
    val compilerOptions = ScalaCompilerOptions.generalize(scalaFacets.map(_.compilerOptions))
    // TODO update settings
  }

  private def deleteScalaProjectLibraries() {
    scalaProjectLibraries.foreach(_.deleteIn(context))
  }

  override def getCreatedFiles = scalaModuleConverter.createdFiles.asJava
}

private object ScalaProjectConverter {
  def findStandardScalaLibraryIn(module: ModuleSettings): Option[LibraryReference] =
    LibraryReference.findAllIn(module).find(_.name.contains("scala-library"))

  private def scalaFacetsIn(context: ConversionContext): Seq[ScalaFacetData] =
    modulesIn(context).flatMap(ScalaFacetData.findIn)

  private def scalaProjectLibrariesIn(context: ConversionContext): Seq[LibraryReference] =
    modulesIn(context).flatMap(scalaProjectLibrariesIn)

  private def modulesIn(context: ConversionContext): Seq[ModuleSettings] =
    context.getModuleFiles.map(context.getModuleSettings).toSeq

  private def scalaProjectLibrariesIn(module: ModuleSettings): Seq[LibraryReference] = {
    val scalaStandardLibrary = findStandardScalaLibraryIn(module)
    val scalaCompilerLibrary = ScalaFacetData.findIn(module).flatMap(_.compilerLibrary)
    scalaStandardLibrary.toSeq ++ scalaCompilerLibrary.toSeq
  }
}
