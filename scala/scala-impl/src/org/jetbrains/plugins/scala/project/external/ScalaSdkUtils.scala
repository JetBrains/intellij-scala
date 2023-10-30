package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project.{LibraryExt, ScalaLibraryProperties, ScalaLibraryType}

import java.io.File

object ScalaSdkUtils {

  def ensureScalaLibraryIsConvertedToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    compilerBridgeBinaryJar: Option[File],
  ): Unit = {
    if (!library.isScalaSdk) {
      // library created but not yet marked as Scala SDK
      ScalaSdkUtils.convertScalaLibraryToScalaSdk(modelsProvider, library, compilerClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar)
    }
    else {
      ScalaSdkUtils.updateScalaLibraryProperties(modelsProvider, library, compilerClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar)
    }
  }

  def ensureScalaLibraryIsConvertedToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    maybeVersion: Option[String],
    compilerBridgeBinaryJar: Option[File],
  ): Unit = {
    if (!library.isScalaSdk) {
      // library created but not yet marked as Scala SDK
      convertScalaLibraryToScalaSdk(modelsProvider, library, compilerClasspath, scaladocExtraClasspath, maybeVersion, compilerBridgeBinaryJar)
    }
    else {
      updateScalaLibraryProperties(modelsProvider, library, compilerClasspath, scaladocExtraClasspath, maybeVersion, compilerBridgeBinaryJar)
    }
  }

  def convertScalaLibraryToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    compilerBridgeBinaryJar: Option[File]
  ): Unit = {
    convertScalaLibraryToScalaSdk(
      modelsProvider,
      library,
      compilerClasspath,
      scaladocExtraClasspath,
      library.libraryVersion,
      compilerBridgeBinaryJar
    )
  }

  private def convertScalaLibraryToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    maybeVersion: Option[String],
    compilerBridgeBinaryJar: Option[File]
  ): Unit = {
    val properties = ScalaLibraryProperties(maybeVersion, compilerClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar)
    val model = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    model.setKind(ScalaLibraryType.Kind)
    model.setProperties(properties)
  }

  private def updateScalaLibraryProperties(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    compilerBridgeBinaryJar: Option[File],
  ): Unit = {
    updateScalaLibraryProperties(modelsProvider, library, compilerClasspath, scaladocExtraClasspath, library.libraryVersion, compilerBridgeBinaryJar)
  }

  private def updateScalaLibraryProperties(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    maybeVersion: Option[String],
    compilerBridgeBinaryJar: Option[File],
  ): Unit = {
    val properties = ScalaLibraryProperties(maybeVersion, compilerClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar)
    val model = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    model.setProperties(properties)
  }
}