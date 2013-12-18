package org.jetbrains.sbt
package project

import java.io.File
import java.util
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.externalSystem.service.project.{ProjectStructureHelper, PlatformFacade}
import com.intellij.openapi.projectRoots.{Sdk, ProjectJdkTable, JavaSdk}
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil._
import collection.JavaConverters._
import org.jetbrains.plugins.scala.components.HighlightingAdvisor
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.pom.java.LanguageLevel
import SbtProjectDataService._
import com.intellij.openapi.roots.impl.{DirectoryIndex, JavaLanguageLevelPusher}
import com.intellij.openapi.diagnostic.Logger

/**
 * @author Pavel Fatin
 */
class SbtProjectDataService(platformFacade: PlatformFacade, helper: ProjectStructureHelper)
  extends AbstractDataService[ScalaProjectData, Project](ScalaProjectData.Key) {

  def doImportData(toImport: util.Collection[DataNode[ScalaProjectData]], project: Project) {
    toImport.asScala.foreach { node =>
      val data = node.getData

      val projectJdk = findJdkBy(data.javaHome).orElse(allJdks.headOption)

      projectJdk.foreach(ProjectRootManager.getInstance(project).setProjectSdk)

      val javacOptions = data.javacOptions

      updateJavaCompilerOptionsIn(project, javacOptions)

      val javaLanguageLevel = javaLanguageLevelFrom(javacOptions)
              .orElse(projectJdk.flatMap(defaultJavaLanguageLevelIn))

      javaLanguageLevel.foreach(upgradeJavaLanguageLevelIn(project, _))
    }

    configureHighlightingIn(project)
  }

  def doRemoveData(toRemove: util.Collection[_ <: Project], project: Project) {}
}

object SbtProjectDataService {
  private val LOG = Logger.getInstance("org.jetbrains.sbt.project.SbtProjectDataService$")

  private val JavaLanguageLevels = Map(
    "1.3" -> LanguageLevel.JDK_1_3,
    "1.4" -> LanguageLevel.JDK_1_4,
    "1.5" -> LanguageLevel.JDK_1_5,
    "1.6" -> LanguageLevel.JDK_1_6,
    "1.7" -> LanguageLevel.JDK_1_7,
    "1.8" -> LanguageLevel.JDK_1_8)
  
  def findJdkBy(home: File): Option[Sdk] = {
    val homePath = toCanonicalPath(home.getAbsolutePath)
    allJdks.find(jdk => homePath.startsWith(toCanonicalPath(jdk.getHomePath)))
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

    val handledOptions = Set("-g:none", "-nowarn", "-Xlint:none", "-deprecation", "-Xlint:deprecation")
    val customOptions = options.filterNot(handledOptions.contains)
    settings.ADDITIONAL_OPTIONS_STRING = customOptions.mkString(" ")
  }

  def javaLanguageLevelFrom(options: Seq[String]): Option[LanguageLevel] = {
    def valueOf(name: String): Option[String] =
      Option(options.indexOf(name)).filterNot(-1 ==).flatMap(options.lift)
    
    valueOf("-source").orElse(valueOf("-target"))
            .flatMap(s => Option(LanguageLevel.parse(s)))
  }

  def defaultJavaLanguageLevelIn(jdk: Sdk): Option[LanguageLevel] = {
    val jdkVersion = jdk.getVersionString

    JavaLanguageLevels.collectFirst {
      case (name, level) if jdkVersion.contains(name) => level
    }
  }

  // TODO don't use reflection when there will be other ways to bypass "reload project" message
  def upgradeJavaLanguageLevelIn(project: Project, level: LanguageLevel) {
    val extension = LanguageLevelProjectExtension.getInstance(project)

    if (!extension.getLanguageLevel.isAtLeast(level)) {
      try {
        val field = extension.getClass.getDeclaredField("myLanguageLevel")
        field.setAccessible(true)
        field.set(extension, level)
        extension.setLanguageLevel(level)

        if (DirectoryIndex.getInstance(project).isInitialized) {
          JavaLanguageLevelPusher.pushLanguageLevel(project)
        }
      }
      catch {
        case e: Exception =>
          //ignore exception, just put to the log
          LOG.info(e)
      }
    }
  }

  def configureHighlightingIn(project: Project) {
    val highlightingSettings = project.getComponent(classOf[HighlightingAdvisor]).getState()
    
    highlightingSettings.TYPE_AWARE_HIGHLIGHTING_ENABLED = true
    highlightingSettings.SUGGEST_TYPE_AWARE_HIGHLIGHTING = false
  }
}