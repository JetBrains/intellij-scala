package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
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
    val compilerBridge = compilerBridgeBinaryJar.orElse(maybeVersion.flatMap(resolveCompilerBridgeJar))
    val properties = ScalaLibraryProperties(maybeVersion, compilerClasspath, scaladocExtraClasspath, compilerBridge)
    val modifiableModel = modelsProvider.getModifiableLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
    if (!library.isScalaSdk) {
      modifiableModel.setKind(ScalaLibraryType.Kind)
    }
    //NOTE: must be called after `setKind` because later resets the properties
    modifiableModel.setProperties(properties)
  }

  def resolveCompilerBridgeJar(scalaVersion: String): Option[File] = {
    if (!scalaVersion.startsWith("3.")) return None

    val compilerBridgeDependency = "org.scala-lang" % "scala3-sbt-bridge" % scalaVersion
    DependencyManager.resolveSafe(compilerBridgeDependency)
      .toOption
      .flatMap(_.headOption)
      .map(_.file)
  }
}