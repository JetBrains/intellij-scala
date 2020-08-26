package org.jetbrains.plugins.scala
package project.gradle

import java.io.File

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.{AbstractDataService, AbstractImporter, Importer}

import scala.jdk.CollectionConverters._

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

    override def importData(): Unit = dataToImport.foreach { scalaNode =>
      Option(scalaNode.getData(ProjectKeys.MODULE)).foreach { moduleData =>
        val moduleName = moduleData.getInternalName

        val maybeCompoundModule = findIdeModule(moduleName)
        val maybeProductionModule = findIdeModule(s"${moduleName}_main").orElse(findIdeModule(s"$moduleName.main"))
        val maybeTestModule = findIdeModule(s"${moduleName}_test").orElse(findIdeModule(s"$moduleName.test"))

        (maybeCompoundModule, maybeProductionModule, maybeTestModule) match {
          case (_, Some(productionModule), Some(testModule)) => configureModules(productionModule, scalaNode, testModule :: Nil)
          case (Some(compoundModule), _, _) => configureModules(compoundModule, scalaNode)
          case _ =>
        }
      }
    }

    private def configureModules(mainModule: Module,
                                 scalaNode: DataNode[ScalaModelData],
                                 otherModules: List[Module] = Nil): Unit = for {
      module <- mainModule :: otherModules
      data = scalaNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("Gradle", compilerOptionsFrom(data))
      module match {
        case `mainModule` => configureScalaSdk(mainModule.getName, data.getScalaClasspath.asScala.toSeq)
        case _ =>
      }
    }

    private def configureScalaSdk(moduleName: String, compilerClasspath: Seq[File]): Unit = {
      def configureScalaSdk(compilerVersion: String): Unit = {
        modelsProvider.getAllLibraries.filter(_.hasRuntimeLibrary).toSet match {
          case libraries if libraries.isEmpty =>
          case libraries =>
            libraries.find(_.compilerVersion.contains(compilerVersion)) match {
              case Some(scalaLibrary) if !scalaLibrary.isScalaSdk => setScalaSdk(scalaLibrary, compilerClasspath)()
              case None => showWarning(ScalaBundle.message("gradle.dataService.scalaLibraryIsNotFound", compilerVersion, moduleName))
              case _ => // do nothing
            }
        }
      }

      import LibraryExt._
      compilerClasspath
        .map(_.getName)
        .filter(isRuntimeLibrary)
        .flatMap(runtimeVersion)
        .headOption
        .fold(showWarning(ScalaBundle.message("gradle.dataService.scalaVersionCantBeDetected", moduleName)))(configureScalaSdk)
    }

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
      val notification = new NotificationData("Gradle Sync", message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC)
      ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification)
    }
  }
}
