package org.jetbrains.plugins.scala
package config

import java.util
import java.io.File
import collection.JavaConverters._
import com.intellij.openapi.externalSystem.model.{ProjectKeys, DataNode}
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import ScalaGradleDataService._
import configuration._

/**
 * @author Pavel Fatin
 */
class ScalaGradleDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
        extends AbstractDataService[ScalaModelData, Library](ScalaModelData.KEY) {

  def doImportData(toImport: util.Collection[DataNode[ScalaModelData]], project: Project) {
    toImport.asScala.foreach { dataNode =>
      val module = {
        val moduleName = dataNode.getData(ProjectKeys.MODULE).getName
        helper.findIdeModule(moduleName, project)
      }

      val scalaData = dataNode.getData

      val compilerClasspath = scalaData.getScalaClasspath.asScala.toSet
//      val compilerOptions = compilerOptionsFrom(scalaData) TODO handle

      val scalaSdk = findScalaSdkIn(project, compilerClasspath).orElse {
        val standardLibrary = findScalaStandardLibraryIn(project, compilerClasspath)
        standardLibrary.map(_.convertToScalaSdkWith(compilerClasspath.toSeq))
      }

      scalaSdk.foreach { properSdk =>
        val existingScalaSdk = module.scalaSdk

        existingScalaSdk match {
          case None =>
            module.attach(properSdk)
          case Some(sdk) if sdk.library != properSdk.library =>
            module.detach(sdk)
            module.attach(properSdk)
          case _ =>
        }
      }
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Library], project: Project) {
    // TODO
  }
}

object ScalaGradleDataService {
  def findScalaSdkIn(project: Project, compilerClasspath: Set[File]): Option[ScalaSdk] =
    project.scalaSdks.find(_.compilerClasspath.toSet == compilerClasspath)

  def findScalaStandardLibraryIn(project: Project, compilerClasspath: Set[File]): Option[Library] = {
    val compilerStandardLibraryFile = compilerClasspath.find(_.getName.startsWith("scala-library"))

    compilerStandardLibraryFile.flatMap(file => project.libraries.find(_.classes.contains(file)))
  }

  def compilerOptionsFrom(data: ScalaModelData): Seq[String] = {
    val options = data.getScalaCompileOptions

    val presentations = Seq(
      options.isDeprecation -> "-deprecation",
      options.isUnchecked -> "-unchecked",
      options.isOptimize -> "-optimise",
      !isEmpty(options.getDebugLevel) -> s"-g:${options.getDebugLevel}",
      !isEmpty(options.getEncoding) -> s"-encoding ${options.getEncoding}",
      !isEmpty(data.getTargetCompatibility) -> s"-target:jvm-${data.getTargetCompatibility}")

    presentations.flatMap((include _).tupled)
  }

  private def isEmpty(s: String) = s == null || s.isEmpty

  private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty
}
