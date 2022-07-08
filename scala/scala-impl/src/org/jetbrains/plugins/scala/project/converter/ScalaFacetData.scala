package org.jetbrains.plugins.scala
package project.converter

import com.intellij.conversion.ModuleSettings
import org.jdom.Element

private case class ScalaFacetData(languageLevel: String,
                                  basePackage: Option[String],
                                  fscEnabled: Boolean,
                                  compilerLibrary: Option[LibraryReference],
                                  maximumHeapSize: Int,
                                  vmOptions: Seq[String],
                                  compilerSettings: ScalaCompilerSettings) {
  def removeFrom(module: ModuleSettings): Unit = {
    val facetElement = ScalaFacetData.scalaFacetElementIn(module).getOrElse(
      throw new IllegalStateException("Cannot remove Scala facet from module: " + module.getModuleName))

    facetElement.detach()
  }
}

private object ScalaFacetData {
  def isPresentIn(module: ModuleSettings): Boolean =
    scalaFacetElementIn(module).isDefined

  private def scalaFacetElementIn(module: ModuleSettings): Option[Element] =
    Option(module.getFacetElement("scala"))

  def findIn(module: ModuleSettings): Option[ScalaFacetData] =
    scalaFacetElementIn(module).map(element => ScalaFacetData(new FacetProperties(element)))

  def apply(properties: FacetProperties): ScalaFacetData = {
    val compilerSettings = ScalaCompilerSettings.from(properties)

    val compilerLibraryId = properties.option("compilerLibraryLevel").flatMap { level =>
      properties.option("compilerLibraryName").map(LibraryReference(Level.fromFacetTitle(level), _))
    }

    new ScalaFacetData(
      languageLevel = properties.string("languageLevel", "Scala_2_12"),
      basePackage = properties.option("basePackage"),
      fscEnabled = properties.boolean("fsc"),
      compilerLibraryId,
      maximumHeapSize = properties.int("maximumHeapSize", 512),
      vmOptions = properties.seq("vmOptions", Seq("-Xss1m", "-server")),
      compilerSettings = compilerSettings)
  }
}
