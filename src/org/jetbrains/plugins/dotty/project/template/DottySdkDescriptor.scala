package org.jetbrains.plugins.dotty.project.template

import java.io.File

import com.intellij.openapi.roots.libraries.LibraryType
import org.jetbrains.plugins.dotty.project.DottyLibraryType
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, ScalaLibraryProperties, Version}
import org.jetbrains.plugins.scala.project.template.{DottyArtifact, Artifact, SdkDescriptor, SdkDescriptorCompanion}

/**
  * @author adkozlov
  */
case class DottySdkDescriptor(version: Option[Version],
                              compilerFiles: Seq[File],
                              libraryFiles: Seq[File],
                              sourceFiles: Seq[File],
                              docFiles: Seq[File]) extends SdkDescriptor {
  override protected val languageName = "dotty"

  override protected val libraryType: LibraryType[ScalaLibraryProperties] = DottyLibraryType.instance

  override protected val libraryName: String = s"$languageName-sdk"

  override protected def libraryProperties: ScalaLibraryProperties = {
    val properties = new ScalaLibraryProperties()

    properties.languageLevel = ScalaLanguageLevel.Dotty
    properties.compilerClasspath = compilerFiles
    properties
  }

  def mainDottyJar = compilerFiles.find { f =>
    val fileName = f.getName
    fileName.startsWith("dotty") && !fileName.startsWith(DottyArtifact.Interfaces.prefix)
  }
}

object DottySdkDescriptor extends SdkDescriptorCompanion {
  override protected val requiredBinaries: Set[Artifact] = DottyArtifact.values

  override protected val libraryArtifacts: Set[Artifact] =
    Set(
      Artifact.ScalaLibrary,
      Artifact.ScalaReflect,
      DottyArtifact.Main,
      DottyArtifact.Interfaces
    )

  override protected def createSdkDescriptor(version: Option[Version],
                                             compilerFiles: Seq[File],
                                             libraryFiles: Seq[File],
                                             sourceFiles: Seq[File],
                                             docFiles: Seq[File]) = {
    DottySdkDescriptor(None, compilerFiles, libraryFiles, sourceFiles, docFiles)
  }
}
