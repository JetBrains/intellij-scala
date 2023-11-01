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
    maybeVersion: Option[String],
    compilerClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    compilerBridgeBinaryJar: Option[File],
  ): Unit = {
    val properties = ScalaLibraryProperties(maybeVersion, compilerClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar)
    val modifiableModel = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    if (!library.isScalaSdk) {
      modifiableModel.setKind(ScalaLibraryType.Kind)
    }
    //NOTE: must be called after `setKind` because later resets the properties
    modifiableModel.setProperties(properties)
  }
}