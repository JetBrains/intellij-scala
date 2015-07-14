package org.jetbrains.sbt
package project.data
package service

import java.util

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.compiler.{CompilerConfiguration, CompilerConfigurationImpl}
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.service.project.{PlatformFacade, ProjectStructureHelper}
import com.intellij.openapi.module.{ModuleManager, ModuleUtil}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.android.sdk.{AndroidPlatform, AndroidSdkType}
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.data
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSystemSettings

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtProjectDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[SbtProjectData, Project](SbtProjectData.Key) {

  def doImportData(toImport: util.Collection[DataNode[SbtProjectData]], project: Project) =
    toImport.asScala.foreach(node => doImportData(project, node.getData))

  def doRemoveData(toRemove: util.Collection[_ <: Project], project: Project) {}

  private def doImportData(project: Project, data: SbtProjectData): Unit = {
    ScalaProjectSettings.getInstance(project).setBasePackages(data.basePackages.asJava)
    configureJdk(project, data)
    updateJavaCompilerOptionsIn(project, data.javacOptions)
    setLanguageLevel(project, data)
    setSbtVersion(project, data)
    updateIncrementalityType(project)
  }

  private def configureJdk(project: Project, data: SbtProjectData): Unit = {
    val existingJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    val projectJdk = data.jdk.flatMap(findJdkBy).orElse(existingJdk).orElse(allJdks.headOption)
    projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)
  }

  private def setLanguageLevel(project: Project, data: SbtProjectData): Unit = {
    val projectJdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
    val javaLanguageLevel = javaLanguageLevelFrom(data.javacOptions)
      .orElse(projectJdk.flatMap(defaultJavaLanguageLevelIn))
    javaLanguageLevel.foreach { level =>
      val extension = LanguageLevelProjectExtension.getInstance(project)
      extension.setLanguageLevel(level)
      extension.setDefault(false)
    }
  }

  private def setSbtVersion(project: Project, data: SbtProjectData): Unit =
    Option(SbtSystemSettings.getInstance(project).getLinkedProjectSettings(data.projectPath))
      .foreach(s => s.sbtVersion = data.sbtVersion)

  private def updateIncrementalityType(project: Project): Unit = {
    val modules = ModuleManager.getInstance(project).getModules
    if (modules.exists(it => ModuleUtil.getModuleType(it) == SharedSourcesModuleType.instance))
      ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.SBT
  }

  private def findJdkBy(sdk: data.Sdk): Option[Sdk] = sdk match {
    case data.Android(version) => findAndroidJdkByVersion(version)
    case data.Jdk(version) => allJdks.find(_.getName.contains(version))
  }

  private def allAndroidSdks: Seq[Sdk] =
    ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance()).asScala

  private def findAndroidJdkByVersion(version: String): Option[Sdk] = {
    def isGEQAsInt(fst: String, snd: String): Boolean =
      try {
        val fstInt = fst.toInt
        val sndInt = snd.toInt
        fstInt >= sndInt
      } catch {
        case exc: NumberFormatException => false
      }

    val matchingSdks = for {
      sdk <- allAndroidSdks
      platformVersion <- Option(AndroidPlatform.getInstance(sdk)).map(_.getApiLevel.toString)
      if (isGEQAsInt(platformVersion, version))
    } yield sdk
    matchingSdks.headOption
  }

  private def allJdks: Seq[Sdk] = ProjectJdkTable.getInstance.getSdksOfType(JavaSdk.getInstance).asScala

  private def updateJavaCompilerOptionsIn(project: Project, options: Seq[String]) {
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

  private def javaLanguageLevelFrom(options: Seq[String]): Option[LanguageLevel] = {
    valueOf("-source", options).flatMap(s => Option(LanguageLevel.parse(s)))
  }

  private def valueOf(name: String, options: Seq[String]): Option[String] =
    Option(options.indexOf(name)).filterNot(-1 == _).flatMap(i => options.lift(i + 1))

  private def additionalOptionsFrom(options: Seq[String]): Seq[String] = {
    val handledOptions = Set("-g:none", "-nowarn", "-Xlint:none", "-deprecation", "-Xlint:deprecation")

    def removePair(name: String, options: Seq[String]): Seq[String] = {
      val index = options.indexOf(name)

      if (index == -1) options else {
        val (prefix, suffix) = options.splitAt(index)
        prefix ++ suffix.drop(2)
      }
    }

    removePair("-source", removePair("-target", options.filterNot(handledOptions.contains)))
  }

  private def defaultJavaLanguageLevelIn(jdk: Sdk): Option[LanguageLevel] = {
    val JavaLanguageLevels = Map(
      "1.3" -> LanguageLevel.JDK_1_3,
      "1.4" -> LanguageLevel.JDK_1_4,
      "1.5" -> LanguageLevel.JDK_1_5,
      "1.6" -> LanguageLevel.JDK_1_6,
      "1.7" -> LanguageLevel.JDK_1_7,
      "1.8" -> LanguageLevel.JDK_1_8,
      "1.9" -> LanguageLevel.JDK_1_9)
    val jdkVersion = jdk.getVersionString

    JavaLanguageLevels.collectFirst {
      case (name, level) if jdkVersion.contains(name) => level
    }
  }
}