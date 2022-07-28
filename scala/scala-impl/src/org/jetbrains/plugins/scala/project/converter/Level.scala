package org.jetbrains.plugins.scala
package project.converter

import com.intellij.conversion.ConversionContext
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar

import scala.jdk.CollectionConverters._

private sealed abstract class Level(val title: String, private val facetTitle: String) {
  def librariesIn(context: ConversionContext): Seq[LibraryData]
}

private object Level {
  val Values = Seq(ApplicationLevel, ProjectLevel, ModuleLevel)

  private val TitleToLevel = Values.map(level => (level.title, level)).toMap
  private val FacetTitleToLevel = Values.map(level => (level.facetTitle, level)).toMap

  def fromTitle(title: String): Level = TitleToLevel.getOrElse(title, new CustomLevel(title))

  def fromFacetTitle(title: String): Level = FacetTitleToLevel.getOrElse(title,
    throw new IllegalArgumentException("Unknown level title: " + title))
}

private object ProjectLevel extends Level("project", "Project") {
  override def librariesIn(context: ConversionContext): Seq[LibraryData] =
    context.getProjectLibrariesSettings.getProjectLibraries.asScala.map(LibraryData(_)).toSeq
}

private object ApplicationLevel extends Level("application", "Global") {
  override def librariesIn(context: ConversionContext): Seq[LibraryData] =
    LibraryTablesRegistrar.getInstance.getLibraryTable.getLibraries.map(LibraryData(_)).toSeq
}

private object ModuleLevel extends Level("module", "Module") {
  override def librariesIn(context: ConversionContext) =
    throw new IllegalArgumentException("Module-level libraries are not supported")
}

private class CustomLevel(title: String) extends Level(title, title) {
  override def librariesIn(context: ConversionContext) =
    throw new IllegalArgumentException("Custom-level libraries are not supported: " + title)
}
