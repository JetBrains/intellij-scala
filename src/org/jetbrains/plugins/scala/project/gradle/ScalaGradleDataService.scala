package org.jetbrains.plugins.scala
package project.gradle

import java.util

import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.scala.project._

import org.jetbrains.sbt.project.data.AbstractDataService

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaGradleDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
        extends AbstractDataService[ScalaModelData, Library](ScalaModelData.KEY) {

  def doImportData(toImport: util.Collection[DataNode[ScalaModelData]], project: Project) {
    toImport.asScala.foreach(doImport(_, project))
  }

  private def doImport(scalaNode: DataNode[ScalaModelData], project: Project) {
    val scalaData = scalaNode.getData

    val module = {
      val moduleData = scalaNode.getData(ProjectKeys.MODULE)
      helper.findIdeModule(moduleData.getExternalName, project)
    }

    module.configureScalaCompilerSettingsFrom("Gradle", ScalaGradleDataService.compilerOptionsFrom(scalaData))

    val compilerClasspath = scalaData.getScalaClasspath.asScala.toSeq

    val compilerVersion = compilerClasspath.map(_.getName)
            .find(_.startsWith("scala-library"))
            .flatMap(JarVersion.findFirstIn)
            .map(Version(_))
            .getOrElse(throw new ExternalSystemException("Cannot determine Scala compiler version for module " +
                         scalaNode.getData(ProjectKeys.MODULE).getExternalName))

    val scalaLibrary = project.libraries
            .filter(_.getName.contains("scala-library"))
            .find(_.scalaVersion == Some(compilerVersion))
            .getOrElse(throw new ExternalSystemException("Cannot find project Scala library " +
            compilerVersion.number + " for module " + scalaNode.getData(ProjectKeys.MODULE).getExternalName))

    if (!scalaLibrary.isScalaSdk) {
      val languageLevel = compilerVersion.toLanguageLevel.getOrElse(ScalaLanguageLevel.Default)
      scalaLibrary.convertToScalaSdkWith(languageLevel, compilerClasspath)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {

  }
}

private object ScalaGradleDataService {
  private def compilerOptionsFrom(data: ScalaModelData): Seq[String] = {
    val options = data.getScalaCompileOptions

    val presentations = Seq(
      options.isDeprecation -> "-deprecation",
      options.isUnchecked -> "-unchecked",
      options.isOptimize -> "-optimise",
      !isEmpty(options.getDebugLevel) -> s"-g:${options.getDebugLevel}",
      !isEmpty(options.getEncoding) -> s"-encoding ${options.getEncoding}",
      !isEmpty(data.getTargetCompatibility) -> s"-target:jvm-${data.getTargetCompatibility}")

    val additionalOptions =
      if (options.getAdditionalParameters != null) options.getAdditionalParameters.asScala else Seq.empty

    presentations.flatMap((include _).tupled) ++ additionalOptions
  }

  private def isEmpty(s: String) = s == null || s.isEmpty

  private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty
}
