package org.jetbrains.plugins.scala
package project.converter

import com.intellij.conversion.ModuleSettings
import org.jdom.Element

/**
 * @author Pavel Fatin
 */
private case class ScalaFacetData(languageLevel: String,
                                  basePackage: Option[String],
                                  fscEnabled: Boolean,
                                  compilerLibrary: Option[LibraryReference],
                                  maximumHeapSize: Int,
                                  vmOptions: Seq[String],
                                  compileOrder: String,
                                  compilerOptions: ScalaCompilerOptions,
                                  compilerPlugins: Seq[String]) {
  def removeFrom(module: ModuleSettings) {
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
    val compilerOptions = new ScalaCompilerOptions(
      warnings = properties.boolean("warnings", default = true),
      deprecationWarnings = properties.boolean("deprecationWarnings"),
      uncheckedWarnings = properties.boolean("uncheckedWarnings"),
      optimiseBytecode = properties.boolean("optimiseBytecode"),
      explainTypeErrors = properties.boolean("optimiseBytecode"),
      continuations = properties.boolean("continuations"),
      debuggingInfoLevel = properties.string("debuggingInfoLevel", "Vars"),
      additionalCompilerOptions = properties.seq("compilerOptions")
    )

    val compilerLibraryId = properties.option("compilerLibraryLevel").flatMap { level =>
      properties.option("compilerLibraryName").map(LibraryReference(Level.fromFacetTitle(level), _))
    }

    new ScalaFacetData(
      languageLevel = properties.string("languageLevel", "SCALA_2_11"),
      basePackage = properties.option("basePackage"),
      fscEnabled = properties.boolean("fsc"),
      compilerLibraryId,
      maximumHeapSize = properties.int("maximumHeapSize", 512),
      vmOptions = properties.seq("vmOptions", Seq("-Xss1m", "-server")),
      compileOrder = properties.string("compileOrder", "Mixed"),
      compilerOptions = compilerOptions,
      compilerPlugins = properties.array("pluginPaths")
    )
  }
}
