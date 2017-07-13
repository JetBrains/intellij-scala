package org.jetbrains.plugins.scala
package project.gradle

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalSystemException, ProjectKeys}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationSource, NotificationCategory, NotificationData}
import com.intellij.openapi.externalSystem.service.project.{IdeModifiableModelsProvider, IdeModelsProvider}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.project.data.service.{Importer, AbstractImporter, AbstractDataService}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaGradleDataService extends AbstractDataService[ScalaModelData, Library](ScalaModelData.KEY) {
  override def createImporter(toImport: Seq[DataNode[ScalaModelData]],
                              projectData: ProjectData,
                              project: Project,
                              modelsProvider: IdeModifiableModelsProvider): Importer[ScalaModelData] =
    new ScalaGradleDataService.Importer(toImport, projectData, project, modelsProvider)
}

private object ScalaGradleDataService {

  private class Importer(dataToImport: Seq[DataNode[ScalaModelData]],
                         projectData: ProjectData,
                         project: Project,
                         modelsProvider: IdeModifiableModelsProvider)
    extends AbstractImporter[ScalaModelData](dataToImport, projectData, project, modelsProvider) {

    override def importData(): Unit =
      dataToImport.foreach(doImport)

    private def doImport(scalaNode: DataNode[ScalaModelData]): Unit =
      for {
        module <- getIdeModulesByNode(scalaNode)
        compilerOptions = compilerOptionsFrom(scalaNode.getData)
        compilerClasspath = scalaNode.getData.getScalaClasspath.asScala.toSeq
      } {
        module.configureScalaCompilerSettingsFrom("Gradle", compilerOptions)
        if (module.getName.endsWith("_main")) {
          configureScalaSdk(module, compilerClasspath)
        }
      }

    private def getIdeModulesByNode(node: DataNode[_]): Seq[Module] = {
      Option(node.getData(ProjectKeys.MODULE))
        .map(_.getInternalName)
        .map(name => findIdeModule(name + "_main").toSeq ++ findIdeModule(name + "_test").toSeq)
        .getOrElse(Seq.empty)
    }

    private def configureScalaSdk(module: Module, compilerClasspath: Seq[File]): Unit = {
      val compilerVersionOption = findScalaLibraryIn(compilerClasspath).flatMap(getVersionFromJar)
      if (compilerVersionOption.isEmpty) {
        showWarning(ScalaBundle.message("gradle.dataService.scalaVersionCantBeDetected", module.getName))
        return
      }
      val compilerVersion = compilerVersionOption.get

      val scalaLibraries = getScalaLibraries
      if (scalaLibraries.isEmpty)
        return

      val scalaLibraryOption = scalaLibraries.find(_.scalaVersion.contains(compilerVersion))
      if (scalaLibraryOption.isEmpty) {
        showWarning(ScalaBundle.message("gradle.dataService.scalaLibraryIsNotFound", compilerVersion.presentation, module.getName))
        return
      }
      val scalaLibrary = scalaLibraryOption.get

      if (!scalaLibrary.isScalaSdk) {
        val languageLevel = scalaLibrary.scalaLanguageLevel.getOrElse(ScalaLanguageLevel.Default)
        convertToScalaSdk(scalaLibrary, Platform.Default, languageLevel, compilerClasspath)
      }
    }

    private def findScalaLibraryIn(classpath: Seq[File]): Option[File] =
      classpath.find(_.getName.startsWith(ScalaLibraryName))

    private def getVersionFromJar(scalaLibrary: File): Option[Version] =
      JarVersion.findFirstIn(scalaLibrary.getName).map(Version(_))

    private def compilerOptionsFrom(data: ScalaModelData): Seq[String] =
      Option(data.getScalaCompileOptions).toSeq.flatMap { options =>
        val presentations = Seq(
          options.isDeprecation -> "-deprecation",
          options.isUnchecked -> "-unchecked",
          options.isOptimize -> "-optimise",
          !isEmpty(options.getDebugLevel) -> s"-g:${options.getDebugLevel}",
          !isEmpty(options.getEncoding) -> s"-encoding",
          // the encoding value needs to be a separate option, otherwise the -encoding flag and the value will be
          // treated as a single flag
          !isEmpty(options.getEncoding) -> options.getEncoding,
          !isEmpty(data.getTargetCompatibility) -> s"-target:jvm-${data.getTargetCompatibility}")

        val additionalOptions =
          if (options.getAdditionalParameters != null) options.getAdditionalParameters.asScala else Seq.empty

        presentations.flatMap((include _).tupled) ++ additionalOptions
      }

    private def isEmpty(s: String) = s == null || s.isEmpty

    private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty

    private def showWarning(message: String): Unit = {
      val notification = new NotificationData("Gradle Sync", message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC);
      ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification);
    }
  }
}
