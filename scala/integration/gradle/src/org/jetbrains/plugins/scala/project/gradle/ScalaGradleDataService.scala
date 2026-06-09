package org.jetbrains.plugins.scala
package project.gradle

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.plugins.gradle.model.data.{GradleSourceSetData, ScalaModelData}
import org.jetbrains.plugins.gradle.util.{GradleConstants, GradleUtil}
import org.jetbrains.plugins.scala.project.*
import org.jetbrains.plugins.scala.project.external.{ScalaAbstractProjectDataService, ScalaSdkUtils}

import java.nio.file.Path
import java.util
import scala.jdk.CollectionConverters.*

class ScalaGradleDataService extends ScalaAbstractProjectDataService[ScalaModelData, Library](ScalaModelData.KEY) {

  private val GradleExternalSystemReadableName = GradleConstants.SYSTEM_ID.getReadableName

  override def importData(
    toImport: util.Collection[? <: DataNode[ScalaModelData]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {
    //TODO remove this in some feature release (probably 2026/2027)
    ScalaSdkUtils.revertScalaSdkFromLibraries(modelsProvider, externalSystemName = GradleExternalSystemReadableName)

    toImport.forEach { scalaNode =>
      Option(scalaNode.getData(ProjectKeys.MODULE)).foreach { moduleData =>
        val gradleSourceSetModules = findGradleSourceSetModules(moduleData, project, modelsProvider)

        val modulesForScalaSDK =
          if (gradleSourceSetModules.isEmpty) {
            val moduleName = moduleData.getInternalName
            modelsProvider.findIdeModuleOpt(moduleName).toSeq
          } else {
            gradleSourceSetModules
          }

        configureModules(scalaNode, modulesForScalaSDK*)(using project, modelsProvider)
      }
    }
  }

  /**
   * Find all Gradle source set modules for the given parent (`moduleData`). <p>
   * A Gradle source set module corresponds to modules like "main", "test", or custom source sets.
   *
   * @note In theory, you can create a custom source set in a Gradle project that uses the Scala plugin without including a Scala library
   *       (e.g., by isolating that source set from the main/test configurations). Such a setup may be compilable and not report any import errors.
   *       In such cases, attaching a Scala SDK to these custom source set modules may not be necessary (but it's not handled at this moment).
   *       This is likely a rare edge case. It could potentially also be a Gradle issue that the missing Scala library in a custom source set module is not reported.
   */
  private def findGradleSourceSetModules(
    moduleData: ModuleData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Seq[Module] = {
    val moduleDataNode = GradleUtil.findGradleModuleData(project, moduleData.getLinkedExternalProjectPath)

    if (moduleDataNode != null) {
      val gradleSourceSets = ExternalSystemApiUtil.getChildren(moduleDataNode, GradleSourceSetData.KEY).asScala
      val gradleSourceSetNames = gradleSourceSets.map(_.getData.getInternalName)
      gradleSourceSetNames.flatMap(modelsProvider.findIdeModuleOpt).toSeq
    } else {
      Nil
    }
  }

  private def configureModules(
    scalaNode: DataNode[ScalaModelData],
    modules: Module*
  )(implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    val scalaData = scalaNode.getData
    val compilerOptions = compilerOptionsFrom(scalaData)
    // Gradle's ScalaModelData#getScalaClasspath only exposes java.io.File; there is no nio.Path-based alternative.
    //noinspection SSBasedInspection
    val classpath = scalaData.getScalaClasspath.asScala.toSeq.map(_.toPath)
    modules.foreach { module =>
      module.configureScalaCompilerSettingsFrom(GradleExternalSystemReadableName, compilerOptions, project)
      configureScalaSdk(module, classpath)
    }
  }

  private def configureScalaSdk(
    module: Module,
    compilerClasspath: Seq[Path]
  )(implicit project: Project, modelsProvider: IdeModifiableModelsProvider): Unit = {
    import LibraryExt.*
    val scalaLibrariesInCompilerClasspath = compilerClasspath.map(_.getFileName.toString).filter(isRuntimeLibrary)
    val compilerVersion = scalaLibrariesInCompilerClasspath.flatMap(runtimeVersion).headOption
    compilerVersion match {
      case Some(version) =>
        configureScalaSdk(project, module, version, compilerClasspath)
      case None        =>
        showWarning(NlsString(ScalaGradleBundle.message("gradle.dataService.scalaVersionCantBeDetected", module.getName)))
    }
  }

  private def configureScalaSdk(
    project: Project,
    module: Module,
    compilerVersion: String,
    compilerClasspath: Seq[Path]
  )(implicit modelsProvider: IdeModifiableModelsProvider): Unit = {
    // Only resolve the compiler bridge for Scala 3. Gradle reports a compiler classpath that doesn't work with
    // the Scala 2.13.12+ compiler bridges, due to clashes.
    val compilerBridgeBinaryJar =
      if (compilerVersion.startsWith("3.")) {
        ScalaSdkUtils.compilerBridgeJarName(compilerVersion).flatMap { bridgeJarName =>
          compilerClasspath.find(_.getFileName.toString == bridgeJarName).orElse(ScalaSdkUtils.resolveCompilerBridgeJar(project, compilerVersion))
        }
      } else None

    val replClasspath = ScalaSdkUtils.resolveReplClasspath(project, compilerVersion)

    ScalaSdkUtils.configureScalaSdk(
      module,
      compilerVersion,
      compilerClasspath,
      scaladocExtraClasspath = Nil,
      compilerBridgeBinaryJar,
      replClasspath,
      sdkPrefix = GradleExternalSystemReadableName,
      modelsProvider
    )
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

      // Gradle's ScalaModelData#getScalaCompilerPlugins only exposes java.io.File; there is no nio.Path-based alternative.
      //noinspection SSBasedInspection
      val scalaCompilerPlugins =
        if (data.getScalaCompilerPlugins ne null)
          data.getScalaCompilerPlugins.asScala.map(f => s"-Xplugin:${f.getPath}").toSeq
        else
          Seq.empty

      val additionalOptions =
        if (options.getAdditionalParameters != null) options.getAdditionalParameters.asScala else Seq.empty

      presentations.flatMap(include.tupled) ++ scalaCompilerPlugins ++ additionalOptions
    }

  private def isEmpty(s: String) = s == null || s.isEmpty

  private def include(b: Boolean, s: String): Seq[String] = if (b) Seq(s) else Seq.empty

  private val Title: NlsString = NlsString(ScalaGradleBundle.message("gradle.sync"))
  private val BalloonGroupId = "Gradle"
  private val BalloonGroup = NotificationGroupManager.getInstance.getNotificationGroup(BalloonGroupId)
  private val SystemId = GradleConstants.SYSTEM_ID

  private def showWarning(message: NlsString)(implicit project: Project): Unit =
    super.showWarning(Title, message, BalloonGroup, SystemId)
}
