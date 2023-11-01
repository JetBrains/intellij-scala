package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project.LibraryExt
import org.jetbrains.plugins.scala.project.external.{ScalaAbstractProjectDataService, ScalaSdkUtils}
import org.jetbrains.sbt.project.data.SbtScalaSdkData

import java.io.File
import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

class SbtScalaSdkDataService extends ScalaAbstractProjectDataService[SbtScalaSdkData, Library](SbtScalaSdkData.Key){

  override def importData(
    toImport: util.Collection[_ <: DataNode[SbtScalaSdkData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider): Unit = {
    val dataToImport = toImport.asScala
    for {
      dataNode <- dataToImport
      module <- modelsProvider.getIdeModuleByNode(dataNode)
      SbtScalaSdkData(scalaVersion, scalacClasspath, scaladocExtraClasspath, compilerBridgeBinaryJar) = dataNode.getData
    } {
      Option(scalaVersion).foreach(configureScalaSdk(
        module,
        _,
        scalacClasspath.asScala.toSeq,
        scaladocExtraClasspath.asScala.toSeq,
        Option(compilerBridgeBinaryJar)
      )(modelsProvider))
    }
  }

  /**
   * Reminder: SbtModuleExtData is built based on `show scalaInstance` sbt command result.
   * In theory looks like if there are no scala libraries in the module, no SbtModuleExtData should be reported for the module
   * But sbt creates `scalaInstance` in such cases anyway
   * see https://github.com/sbt/sbt/issues/6559
   * Also e.g. for Scala 3 (dotty) project, there is not explicit scala3-library dependency in modules,
   * because all modules already depend on scala3-module in the Scala3 project itself
   * So scalaInstance is reported for modules only as compiler which should be used to compile sources
   */
  private def configureScalaSdk(
    module: Module,
    compilerVersion: String,
    scalacClasspath: Seq[File],
    scaladocExtraClasspath: Seq[File],
    compilerBridgeBinaryJar: Option[File],
  )(
    implicit modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    val scalaSDKLibraryName = s"sbt: scala-sdk-$compilerVersion"
    val libraries = modelsProvider.getModifiableProjectLibrariesModel.getLibraries
    val existingScalaSDKForSpecificVersion = libraries.find { lib =>
      lib.getName == scalaSDKLibraryName && lib.isScalaSdk
    }
    val scalaSdkLibrary = existingScalaSDKForSpecificVersion.getOrElse {
      val tableModel = modelsProvider.getModifiableProjectLibrariesModel
      tableModel.createLibrary(scalaSDKLibraryName)
    }
    ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
      modelsProvider,
      scalaSdkLibrary,
      scalaSdkLibrary.libraryVersion,
      scalacClasspath,
      scaladocExtraClasspath,
      compilerBridgeBinaryJar
    )

    val rootModel = modelsProvider.getModifiableRootModel(module)
    rootModel.addLibraryEntry(scalaSdkLibrary)
  }
}
