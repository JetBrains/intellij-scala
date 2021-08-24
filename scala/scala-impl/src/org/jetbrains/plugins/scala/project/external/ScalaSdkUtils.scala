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
    compilerClasspath: Seq[File]
  ): Unit = {
    if (!library.isScalaSdk) {
      // library created but not yet marked as Scala SDK
      ScalaSdkUtils.convertScalaLibraryToScalaSdk(modelsProvider, library, compilerClasspath)
    }
    else {
      ScalaSdkUtils.updateScalaLibraryProperties(modelsProvider, library, compilerClasspath)
    }
  }

  def ensureScalaLibraryIsConvertedToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    maybeVersion: Option[String]
  ): Unit = {
    if (!library.isScalaSdk) {
      // library created but not yet marked as Scala SDK
      convertScalaLibraryToScalaSdk(modelsProvider, library, compilerClasspath, maybeVersion)
    }
    else {
      updateScalaLibraryProperties(modelsProvider, library, compilerClasspath, maybeVersion)
    }
  }

  private def convertScalaLibraryToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File]
  ): Unit = {
    convertScalaLibraryToScalaSdk(modelsProvider, library, compilerClasspath, library.compilerVersion)
  }

  private def convertScalaLibraryToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    maybeVersion: Option[String]
  ): Unit = {
    val properties = ScalaLibraryProperties(maybeVersion, compilerClasspath)
    val model = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    model.setKind(ScalaLibraryType.Kind)
    model.setProperties(properties)
  }

  private def updateScalaLibraryProperties(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
  ): Unit = {
    updateScalaLibraryProperties(modelsProvider, library, compilerClasspath, library.compilerVersion)
  }

  private def updateScalaLibraryProperties(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File],
    maybeVersion: Option[String]
  ): Unit = {
    val properties = ScalaLibraryProperties(maybeVersion, compilerClasspath)
    val model = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    model.setProperties(properties)
  }
}