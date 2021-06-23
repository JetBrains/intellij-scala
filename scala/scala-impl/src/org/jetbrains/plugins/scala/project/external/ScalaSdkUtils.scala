package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project.{LibraryExt, ScalaLibraryProperties, ScalaLibraryType}

import java.io.File

object ScalaSdkUtils {

  def convertScalaLibraryToScalaSdk(
    modelsProvider: IdeModifiableModelsProvider,
    library: Library,
    compilerClasspath: Seq[File]
  ): Unit = {
    convertScalaLibraryToScalaSdk(modelsProvider, library, compilerClasspath, library.compilerVersion)
  }

  def convertScalaLibraryToScalaSdk(
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
}