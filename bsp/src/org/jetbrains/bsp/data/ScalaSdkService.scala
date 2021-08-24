package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.{ScalaAbstractProjectDataService, ScalaSdkUtils}

import java.io.File
import scala.jdk.CollectionConverters._

class ScalaSdkService extends ScalaAbstractProjectDataService[ScalaSdkData, Library](ScalaSdkData.Key) {

  override final def importData(
    toImport: java.util.Collection[_ <: DataNode[ScalaSdkData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    toImport.forEach(doImport(_)(modelsProvider))
  }

  private def doImport(dataNode: DataNode[ScalaSdkData])
                      (implicit modelsProvider: IdeModifiableModelsProvider): Unit =
    for {
      module <- modelsProvider.getIdeModuleByNode(dataNode)
    } {
      val ScalaSdkData(_, scalaVersion, scalacClasspath, scalacOptions) = dataNode.getData
      module.configureScalaCompilerSettingsFrom("bsp", scalacOptions.asScala)
      configureScalaSdk(
        module,
        Option(scalaVersion),
        scalacClasspath.asScala.toSeq
      )
    }

  private def configureScalaSdk(
    module: Module,
    maybeVersion: Option[String],
    compilerClasspath: Seq[File]
  )(implicit modelsProvider: IdeModifiableModelsProvider): Unit = for {
    presentation <- maybeVersion
    if ScalaLanguageLevel.findByVersion(presentation).isDefined

    library <- scalaLibraries(module, modelsProvider)
  } {
    ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
      modelsProvider,
      library,
      compilerClasspath,
      Some(presentation)
    )
  }

  private def scalaLibraries(module: Module, modelsProvider: IdeModifiableModelsProvider) =
    modelsProvider.getModifiableRootModel(module)
      .getModuleLibraryTable
      .getLibraries
      .find(_.getName.contains(ScalaSdkData.LibraryName))
}
