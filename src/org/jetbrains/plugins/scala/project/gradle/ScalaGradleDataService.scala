package org.jetbrains.plugins.scala
package project.gradle

import java.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.scala.project._

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

    val compilerOptions = ScalaGradleDataService.compilerOptionsFrom(scalaData)
    project.scalaCompilerSettigns.configureFrom(compilerOptions)

    val compilerClasspath = scalaData.getScalaClasspath.asScala.toSeq

    val compilerVersion = compilerClasspath.map(_.getName)
            .find(_.startsWith("scala-library"))
            .flatMap(JarVersion.findFirstIn)
            .map(Version(_))

    compilerVersion.foreach { version =>
      val matchedLibrary = project.libraries.find(_.scalaVersion == Some(version))

      for (library <- matchedLibrary if !library.isScalaSdk) {
        val languageLevel = ScalaLanguageLevel.from(version).getOrElse(ScalaLanguageLevel.Default)
        library.convertToScalaSdkWith(languageLevel, compilerClasspath)
      }
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

    presentations.flatMap((include _).tupled) ++ options.getAdditionalParameters.asScala
  }

  private def isEmpty(s: String) = s == null || s.isEmpty

  private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty
}
