package org.jetbrains.plugins.scala
package project.converter

import java.io.File
import com.intellij.conversion.{ConversionContext, ModuleSettings, ConversionProcessor}
import org.jetbrains.plugins.scala.project.converter.ScalaModuleConversionProcessor._

/**
 * @author Pavel Fatin
 */
private class ScalaModuleConversionProcessor(context: ConversionContext) extends ConversionProcessor[ModuleSettings] {
  private var createdSdks: Seq[ScalaSdkData] = Seq.empty
  private var newSdkFiles: Seq[File] = Seq.empty
  
  def isConversionNeeded(module: ModuleSettings) = ScalaFacetData.isPresentIn(module)

  def process(module: ModuleSettings) {
    val scalaFacet = ScalaFacetData.findIn(module).getOrElse(
      throw new IllegalStateException("Cannot find Scala facet in module: " + module.getModuleName))

    val scalaStandardLibraryReference = ScalaProjectConverter.findStandardScalaLibraryIn(module)
    val scalaStandardLibrary = scalaStandardLibraryReference.flatMap(_.resolveIn(context))
    val scalaCompilerLibrary = scalaFacet.compilerLibrary.flatMap(_.resolveIn(context))

    scalaCompilerLibrary.foreach { compilerLibrary =>
      val existingScalaSdk = createdSdks.find(_.isEquivalentTo(compilerLibrary))

      val scalaSdk = existingScalaSdk.getOrElse {
        val name = scalaStandardLibrary.map(library => transform(library.name)).getOrElse("scala-sdk")
        val standardLibrary = scalaStandardLibrary.getOrElse(LibraryData.empty)
        val compilerClasspath = compilerLibrary.classesAsFileUrls
        val languageLevel = ScalaSdkData.languageLevelFrom(compilerClasspath)
        val sdk = ScalaSdkData(name, standardLibrary, languageLevel, compilerClasspath)
        createdSdks :+= sdk
        newSdkFiles ++= sdk.createIn(context)
        sdk
      }

      scalaSdk.addReferenceTo(module)
    }

    scalaStandardLibraryReference.foreach(_.removeFrom(module))
    scalaFacet.removeFrom(module)
  }
  
  def createdFiles: Seq[File] = newSdkFiles
}

object ScalaModuleConversionProcessor {
  private val BuildTools = Set("sbt", "maven", "gradle")

  def transform(name: String): String = {
    val name0 = name.toLowerCase

    if (BuildTools.exists(name0.startsWith)) {
      name
    } else {
      name.replaceFirst("library", "sdk")
    }
  }
}