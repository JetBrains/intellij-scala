package org.jetbrains.plugins.scala.project.bsp

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import org.jetbrains.plugins.bsp.scala.sdk.{ScalaSdk, ScalaSdkExtension}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.project.external.ScalaSdkUtils

import java.net.URI
import java.nio.file.Paths

class ScalaSdkExtensionImpl extends ScalaSdkExtension {

  override def addScalaSdk(scalaSdk: ScalaSdk, ideModifiableModelsProvider: IdeModifiableModelsProvider): Unit = {
    if (ScalaLanguageLevel.findByVersion(scalaSdk.getScalaVersion).isEmpty) return

    val scalaSdkName = scalaSdk.getName
    val projectLibrariesModel = ideModifiableModelsProvider.getModifiableProjectLibrariesModel
    val existingScalaLibrary = projectLibrariesModel.getLibraries.find(_.getName == scalaSdkName)
    val sdkJars = scalaSdk.getSdkJars.toArray().map(uri => Paths.get(URI.create(uri.toString)).toFile)
    val scalaLibrary = existingScalaLibrary.getOrElse(projectLibrariesModel.createLibrary(scalaSdkName))

    ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
      modelsProvider = ideModifiableModelsProvider,
      library = scalaLibrary,
      maybeVersion = Some(scalaSdk.getScalaVersion),
      compilerClasspath = sdkJars.toSeq,
      scaladocExtraClasspath = Nil,
      compilerBridgeBinaryJar = None
    )
  }
}
