package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.DependencyManager
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.project.{LibraryExt, ScalaLibraryProperties, ScalaLibraryType, Version}

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

  def resolveCompilerBridgeJar(scalaVersion: String): Option[File] =
    compilerBridgeName(scalaVersion)
      .map(name => "org.scala-lang" % name % scalaVersion)
      .flatMap(dep => DependencyManager.resolveSafe(dep).toOption)
      .flatMap(_.headOption)
      .map(_.file)

  def compilerBridgeName(scalaVersion: String): Option[String] = {
    val version = Version(scalaVersion)
    if (version.major(1) == Version("2")) {
      if (version >= Version("2.13.12")) {
        // Scala 2.13.12 and later versions distribute their own precompiled compiler bridge with support for
        // compiler diagnostics.
        Some(Scala2CompilerBridgeName)
      } else
        None // Previous Scala 2 versions should use the bundled source based Zinc compiler bridge
    } else Some(Scala3CompilerBridgeName)
  }

  def compilerBridgeJarName(scalaVersion: String): Option[String] =
    compilerBridgeName(scalaVersion).map(n => s"$n-$scalaVersion.jar")

  private final val Scala3CompilerBridgeName = "scala3-sbt-bridge"

  private final val Scala2CompilerBridgeName = "scala2-sbt-bridge"
}