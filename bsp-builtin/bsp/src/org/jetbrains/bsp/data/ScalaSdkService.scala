package org.jetbrains.bsp.data

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.bsp.BSP
import org.jetbrains.plugins.scala.project.*
import org.jetbrains.plugins.scala.project.external.{ScalaAbstractProjectDataService, ScalaSdkUtils}

import java.nio.file.Path
import scala.collection.mutable

class ScalaSdkService extends ScalaAbstractProjectDataService[ScalaSdkData, Library](ScalaSdkData.Key) {

  override final def importData(
    toImport: java.util.Collection[? <: DataNode[ScalaSdkData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    // The REPL classpath is a transitive resolve which bypasses the local Ivy cache.
    // We resolve the REPL classpath of each Scala version once and cache it for the duration of the project import.
    val replClasspathCache = mutable.HashMap.empty[String, ReplClasspath]
    def resolveReplClasspath(scalaVersion: String): ReplClasspath =
      replClasspathCache.getOrElseUpdate(scalaVersion, ScalaSdkUtils.resolveReplClasspath(project, scalaVersion))

    toImport.forEach(doImport(_, project, resolveReplClasspath)(using modelsProvider))
  }

  private def doImport(dataNode: DataNode[ScalaSdkData], project: Project, resolveReplClasspath: String => ReplClasspath)
                      (implicit modelsProvider: IdeModifiableModelsProvider): Unit =
    for {
      module <- modelsProvider.getIdeModuleByNode(dataNode)
    } {
      val ScalaSdkData(_, scalaVersion, scalacClasspath, _, scalacOptions) = dataNode.getData
      module.configureScalaCompilerSettingsFrom("bsp", scalacOptions, project)
      configureScalaSdk(
        project,
        module,
        scalaVersion,
        scalacClasspath.map(_.toPath),
        resolveReplClasspath
      )
    }

  private def configureScalaSdk(
    project: Project,
    module: Module,
    scalaVersionOpt: Option[String],
    compilerClasspath: Seq[Path],
    resolveReplClasspath: String => ReplClasspath
  )(implicit modelsProvider: IdeModifiableModelsProvider): Unit = for {
    scalaVersion <- scalaVersionOpt
    if ScalaLanguageLevel.findByVersion(scalaVersion).isDefined
  } {
    val compilerBridgeBinaryJar = ScalaSdkUtils.resolveCompilerBridgeJar(project, scalaVersion)
    val replClasspath = resolveReplClasspath(scalaVersion)
    ScalaSdkUtils.configureScalaSdk(
      module,
      scalaVersion,
      compilerClasspath,
      // TODO: currently we agreed that BSP implementation should just omit Scala3 doc jars in `ScalaBuildTarget.jars` field
      //  and we should probably create a separate request to obtain scaladoc classpath
      //  see https://github.com/build-server-protocol/build-server-protocol/issues/229
      scaladocExtraClasspath = Nil,
      compilerBridgeBinaryJar = compilerBridgeBinaryJar,
      replClasspath = replClasspath,
      BSP.Name,
      modelsProvider
    )
  }
}
