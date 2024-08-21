package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.bsp.BSP
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
      val ScalaSdkData(_, scalaVersion, scalacClasspath, _, scalacOptions) = dataNode.getData
      module.configureScalaCompilerSettingsFrom("bsp", scalacOptions.asScala)
      configureScalaSdk(
        module,
        Option(scalaVersion),
        scalacClasspath.asScala.toSeq
      )
    }

  private def configureScalaSdk(
    module: Module,
    scalaVersionOpt: Option[String],
    compilerClasspath: Seq[File]
  )(implicit modelsProvider: IdeModifiableModelsProvider): Unit = for {
    scalaVersion <- scalaVersionOpt
    if ScalaLanguageLevel.findByVersion(scalaVersion).isDefined
  } {
    val scalaSdkLibrary = ScalaSdkUtils.getOrCreateScalaSdkLibrary(modelsProvider, BSP.ProjectSystemId, scalaVersion)
    val compilerBridgeBinaryJar = ScalaSdkUtils.resolveCompilerBridgeJar(scalaVersion)
    ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
      modelsProvider,
      scalaSdkLibrary,
      maybeVersion = Some(scalaVersion),
      compilerClasspath,
      // TODO: currently we agreed that BSP implementation should just omit Scala3 doc jars in `ScalaBuildTarget.jars` field
      //  and we should probably create a separate request to obtain scaladoc classpath
      //  see https://github.com/build-server-protocol/build-server-protocol/issues/229
      scaladocExtraClasspath = Nil,
      compilerBridgeBinaryJar = compilerBridgeBinaryJar
    )

    val rootModel = modelsProvider.getModifiableRootModel(module)
    rootModel.addLibraryEntry(scalaSdkLibrary)
  }
}
