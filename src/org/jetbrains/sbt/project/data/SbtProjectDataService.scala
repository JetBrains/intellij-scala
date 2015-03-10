package org.jetbrains.sbt
package project.data

import java.util

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.compiler.{CompilerConfiguration, CompilerConfigurationImpl}
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.{ModuleUtil, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.android.sdk.{AndroidPlatform, AndroidSdkType}
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.sbt.project.data.SbtProjectDataService._
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSystemSettings

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtProjectDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaProjectData, Project](ScalaProjectData.Key) {

  def doImportData(toImport: util.Collection[DataNode[ScalaProjectData]], project: Project) {
    toImport.asScala.foreach { node =>
      val data = node.getData

      val existingJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)

      val projectJdk = data.jdk.flatMap(findJdkBy).orElse(existingJdk).orElse(allJdks.headOption)

      projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)

      val javacOptions = data.javacOptions

      updateJavaCompilerOptionsIn(project, javacOptions)

      val javaLanguageLevel = javaLanguageLevelFrom(javacOptions)
              .orElse(projectJdk.flatMap(defaultJavaLanguageLevelIn))

      javaLanguageLevel.foreach(updateJavaLanguageLevelIn(project, _))

      Option(SbtSystemSettings.getInstance(project).getLinkedProjectSettings(data.projectPath)).foreach { s =>
        s.sbtVersion = data.sbtVersion
      }

      val modules = ModuleManager.getInstance(project).getModules
      if (modules.exists(it => ModuleUtil.getModuleType(it) == SharedSourcesModuleType.instance)) {
        ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.SBT
      }
    }
  }

  def doRemoveData(toRemove: util.Collection[_ <: Project], project: Project) {}
}

object SbtProjectDataService {
  private val JavaLanguageLevels = Map(
    "1.3" -> LanguageLevel.JDK_1_3,
    "1.4" -> LanguageLevel.JDK_1_4,
    "1.5" -> LanguageLevel.JDK_1_5,
    "1.6" -> LanguageLevel.JDK_1_6,
    "1.7" -> LanguageLevel.JDK_1_7,
    "1.8" -> LanguageLevel.JDK_1_8,
    "1.9" -> LanguageLevel.JDK_1_9)
  
  def findJdkBy(sdk: ScalaProjectData.Sdk): Option[Sdk] = sdk match {
    case ScalaProjectData.Android(version) => findAndroidJdkByVersion(version)
    case ScalaProjectData.Jdk(version) => Option(ProjectJdkTable.getInstance().findJdk(version))
  }

  def findAndroidJdkByVersion(version: String): Option[Sdk] = {
    val sdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance()).asScala
    for (sdk <- sdks) {
      val platformVersion = Option(AndroidPlatform.getInstance(sdk)).map { _.getApiLevel.toString }
      if (platformVersion.fold(false){ _ == version })
        return Some(sdk)
    }
    None
  }

  def allJdks: Seq[Sdk] = ProjectJdkTable.getInstance.getSdksOfType(JavaSdk.getInstance).asScala
  
  def updateJavaCompilerOptionsIn(project: Project, options: Seq[String]) {
    val settings = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])

    def contains(values: String*) = values.exists(options.contains)

    if (contains("-g:none")) {
      settings.DEBUGGING_INFO = false
    }

    if (contains("-nowarn", "-Xlint:none")) {
      settings.GENERATE_NO_WARNINGS = true
    }

    if (contains("-deprecation", "-Xlint:deprecation")) {
      settings.DEPRECATION = true
    }

    valueOf("-target", options).foreach { target =>
      val compilerSettings = CompilerConfiguration.getInstance(project).asInstanceOf[CompilerConfigurationImpl]
      compilerSettings.setProjectBytecodeTarget(target)
    }

    val customOptions = additionalOptionsFrom(options)

    settings.ADDITIONAL_OPTIONS_STRING = customOptions.mkString(" ")
  }

  def javaLanguageLevelFrom(options: Seq[String]): Option[LanguageLevel] = {
    valueOf("-source", options).flatMap(s => Option(LanguageLevel.parse(s)))
  }

  def valueOf(name: String, options: Seq[String]): Option[String] =
    Option(options.indexOf(name)).filterNot(-1 == _).flatMap(i => options.lift(i + 1))

  def additionalOptionsFrom(options: Seq[String]): Seq[String] = {
    val handledOptions = Set("-g:none", "-nowarn", "-Xlint:none", "-deprecation", "-Xlint:deprecation")

    removePair("-source", removePair("-target", options.filterNot(handledOptions.contains)))
  }

  def removePair(name: String, options: Seq[String]): Seq[String] = {
    val index = options.indexOf(name)

    if (index == -1) options else {
      val (prefix, suffix) = options.splitAt(index)
      prefix ++ suffix.drop(2)
    }
  }

  def defaultJavaLanguageLevelIn(jdk: Sdk): Option[LanguageLevel] = {
    val jdkVersion = jdk.getVersionString

    JavaLanguageLevels.collectFirst {
      case (name, level) if jdkVersion.contains(name) => level
    }
  }

  def updateJavaLanguageLevelIn(project: Project, level: LanguageLevel) {
    val extension = LanguageLevelProjectExtension.getInstance(project)
    extension.setLanguageLevel(level)
  }
}