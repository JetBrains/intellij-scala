package org.jetbrains.plugins.scala
package project.gradle

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.ScalaModelData
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.{ScalaAbstractProjectDataService, ScalaSdkUtils}

import java.nio.file.Path
import java.util
import scala.jdk.CollectionConverters._

class ScalaGradleDataService extends ScalaAbstractProjectDataService[ScalaModelData, Library](ScalaModelData.KEY) {

  override def importData(
    toImport: util.Collection[_ <: DataNode[ScalaModelData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    implicit val p: Project = project
    implicit val mp: IdeModifiableModelsProvider = modelsProvider
    toImport.forEach { scalaNode =>
      Option(scalaNode.getData(ProjectKeys.MODULE)).foreach { moduleData =>
        val moduleName = moduleData.getInternalName

        val maybeCompoundModule   = modelsProvider.findIdeModuleOpt(moduleName)
        val maybeProductionModule = modelsProvider.findIdeModuleOpt(s"${moduleName}_main").orElse(modelsProvider.findIdeModuleOpt(s"$moduleName.main"))
        val maybeTestModule       = modelsProvider.findIdeModuleOpt(s"${moduleName}_test").orElse(modelsProvider.findIdeModuleOpt(s"$moduleName.test"))

        (maybeCompoundModule, maybeProductionModule, maybeTestModule) match {
          case (_, Some(productionModule), Some(testModule)) => configureModules(productionModule, scalaNode, testModule :: Nil)
          case (Some(compoundModule), _, _)                  => configureModules(compoundModule, scalaNode)
          case _                                             =>
        }
      }
    }
  }

  private def configureModules(
    mainModule: Module,
    scalaNode: DataNode[ScalaModelData],
    otherModules: List[Module] = Nil
  )(implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit =
    for {
      module <- mainModule :: otherModules
      data = scalaNode.getData
    } {
      module.configureScalaCompilerSettingsFrom("Gradle", compilerOptionsFrom(data))
      module match {
        case `mainModule` =>
          configureScalaSdk(mainModule, data.getScalaClasspath.asScala.toSeq.map(_.toPath))
        case _ =>
      }
    }

  private def configureScalaSdk(
    module: Module,
    compilerClasspath: Seq[Path]
  )(implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    import LibraryExt._
    val scalaLibrariesInCompilerClasspath = compilerClasspath.map(_.getFileName.toString).filter(isRuntimeLibrary)
    val compilerVersion = scalaLibrariesInCompilerClasspath.flatMap(runtimeVersion).headOption
    compilerVersion match {
      case Some(version) =>
        configureScalaSdk(module, version, compilerClasspath)
      case None        =>
        showWarning(NlsString(ScalaGradleBundle.message("gradle.dataService.scalaVersionCantBeDetected", module.getName)))
    }
  }

  private def configureScalaSdk(
    module: Module,
    compilerVersion: String,
    compilerClasspath: Seq[Path]
  )(implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    val projectLevelLibraries = modelsProvider.getAllLibraries
    val moduleLevelLibraries = modelsProvider.getModifiableRootModel(module).getModuleLibraryTable.getLibraries
    val scalaLibraries = (moduleLevelLibraries ++ projectLevelLibraries).toSet.filter(_.hasRuntimeLibrary)
    if (scalaLibraries.nonEmpty) {
      val scalaLibraryWithSameVersion = scalaLibraries.find { library =>
        library.libraryVersion.contains(compilerVersion) || library.jarLibraryVersion.contains(compilerVersion)
      }
      scalaLibraryWithSameVersion match {
        case Some(library) =>
          // Only resolve the compiler bridge for Scala 3. Gradle reports a compiler classpath that doesn't work with
          // the Scala 2.13.12+ compiler bridges, due to clashes.
          val compilerBridgeBinaryJar = library.libraryVersion.filter(_.startsWith("3.")).flatMap { version =>
            ScalaSdkUtils.compilerBridgeJarName(version).flatMap { bridgeJarName =>
              compilerClasspath.find(_.getFileName.toString == bridgeJarName).orElse(ScalaSdkUtils.resolveCompilerBridgeJar(version))
            }
          }

          ScalaSdkUtils.ensureScalaLibraryIsConvertedToScalaSdk(
            modelsProvider,
            library,
            compilerClasspath,
            scaladocExtraClasspath = Nil, // TODO SCL-17219
            compilerBridgeBinaryJar
          )
        case None =>
          showScalaLibraryNotFoundWarning(compilerVersion, module)
      }
    } else {
      // In practice, this warning shouldn’t be displayed because if the Scala library is missing, a built-in message is shown instead.
      showScalaLibraryNotFoundWarning(compilerVersion, module)
    }
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
        !isEmpty(options.getEncoding) -> options.getEncoding
      )

      val scalaCompilerPlugins =
        if (data.getScalaCompilerPlugins ne null)
          data.getScalaCompilerPlugins.asScala.map(f => s"-Xplugin:${f.getPath}").toSeq
        else
          Seq.empty

      val additionalOptions =
        if (options.getAdditionalParameters != null) options.getAdditionalParameters.asScala else Seq.empty

      presentations.flatMap((include _).tupled) ++ scalaCompilerPlugins ++ additionalOptions
    }

  private def isEmpty(s: String) = s == null || s.isEmpty

  private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty

  private val Title: NlsString = NlsString(ScalaGradleBundle.message("gradle.sync"))
  private val BalloonGroupId = "Gradle"
  private val BalloonGroup = NotificationGroupManager.getInstance.getNotificationGroup(BalloonGroupId)
  private val SystemId = GradleConstants.SYSTEM_ID

  private def showWarning(message: NlsString)(implicit project: Project): Unit =
    super.showWarning(Title, message, BalloonGroup, SystemId)

  private def showScalaLibraryNotFoundWarning(
    version: String,
    module: Module
  )(implicit project: Project): Unit =
    showScalaLibraryNotFoundWarning(Title, version, module.getName, BalloonGroup, SystemId)
}
