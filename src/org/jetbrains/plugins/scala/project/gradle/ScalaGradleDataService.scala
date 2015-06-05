package org.jetbrains.plugins.scala
package project.gradle

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.scala.project._

import org.jetbrains.sbt.project.data.{AbstractDataService, SafeProjectStructureHelper}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaGradleDataService(val helper: ProjectStructureHelper)
        extends AbstractDataService[ScalaModelData, Library](ScalaModelData.KEY)
        with SafeProjectStructureHelper {

  import ScalaGradleDataService._

  def doImportData(toImport: util.Collection[DataNode[ScalaModelData]], project: Project) {
    toImport.asScala.foreach(doImport(_, project))
  }

  private def doImport(scalaNode: DataNode[ScalaModelData], project: Project): Unit =
    for {
      module <- getIdeModuleByNode(scalaNode, project)
      compilerOptions = compilerOptionsFrom(scalaNode.getData)
      compilerClasspath = scalaNode.getData.getScalaClasspath.asScala.toSeq
    } {
      module.configureScalaCompilerSettingsFrom("Gradle", compilerOptions)
      configureScalaSdk(project, module, compilerClasspath)
    }

  private def configureScalaSdk(project: Project, module: Module, compilerClasspath: Seq[File]): Unit = {
    val compilerVersion =
      findScalaLibraryIn(compilerClasspath).flatMap(getVersionFromJar)
        .getOrElse(throw new ExternalSystemException("Cannot determine Scala compiler version for module " + module.getName))

    val scalaLibrary =
      findAllScalaLibrariesIn(project).find(_.scalaVersion == Some(compilerVersion))
        .getOrElse(throw new ExternalSystemException("Cannot find project Scala library " + compilerVersion.number + " for module " + module.getName))

    if (!scalaLibrary.isScalaSdk) {
      val languageLevel = scalaLibrary.scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Default)
      scalaLibrary.convertToScalaSdkWith(languageLevel, compilerClasspath)
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {}
}

private object ScalaGradleDataService {

  private val ScalaLibraryName = "scala-library"

  private def findScalaLibraryIn(classpath: Seq[File]): Option[File] =
    classpath.find(_.getName.startsWith(ScalaLibraryName))

  private def getVersionFromJar(scalaLibrary: File): Option[Version] =
    JarVersion.findFirstIn(scalaLibrary.getName).map(Version(_))

  private def findAllScalaLibrariesIn(project: Project): Seq[Library] =
    project.libraries.filter(_.getName.contains(ScalaLibraryName))

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
