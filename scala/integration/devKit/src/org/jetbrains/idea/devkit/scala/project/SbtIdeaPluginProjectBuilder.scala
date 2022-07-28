package org.jetbrains.idea.devkit.scala.project

import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SettingsStep}
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.idea.devkit.scala.DevkitBundle
import org.jetbrains.idea.devkit.scala.project.SbtIdeaPluginProjectBuilder._
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.sbt.project.template.AbstractArchivedSbtProjectBuilder
import org.jetbrains.sbt.project.template.AbstractArchivedSbtProjectBuilder.SbtPatternExt

import java.io.File
import java.net.URL
import scala.io.Source
import scala.util.Try

final class SbtIdeaPluginProjectBuilder extends AbstractArchivedSbtProjectBuilder {

  private val LOG = Logger.getInstance(getClass)

  private val GH_WITH_EXAMPLES_URL  = "https://github.com/JetBrains/sbt-idea-example/archive/examples.zip"
  private val GH_NO_EXAMPLES_URL    = "https://github.com/JetBrains/sbt-idea-example/archive/noexamples.zip"
  private def FALLBACK_URL          = getClass.getClassLoader.getResource("example_projects/sbt-idea-example.zip")

  override def archiveURL: URL = {
    if (newProjectSettings.includeSamples)
      new URL(GH_WITH_EXAMPLES_URL)
    else
      new URL(GH_NO_EXAMPLES_URL)
  }

  override protected def extractArchive(root: File, url: URL, unwrapSingleTopLevelFolder: Boolean = false): Unit = {
    try {
      super.extractArchive(root, url, unwrapSingleTopLevelFolder = true)
    } catch {
      case e: Exception =>
        LOG.error("Couldn't fetch template archive from GH. Using bundled", e)
        super.extractArchive(root, FALLBACK_URL)
    }
  }

  private var newProjectSettings = NewProjectSettings(
    includeSamples = true,
    DevkitBundle.message("sbtidea.template.default.name"),
    DevkitBundle.message("sbtidea.template.default.vendor"),
    Try(ApplicationInfo.getInstance().getBuild.withoutProductCode().asString()).getOrElse("LATEST-EAP-SNAPSHOT"),
    SbtIdeaPluginPlatformKind.IdeaCommunity
  )

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    val projectNameField = settingsStep match {
      case projectStep: ProjectSettingsStep =>
        projectStep.getModuleNameField
      case other =>
        throw new IllegalStateException(s"Can't get new project name field. Unexpected parent step: ${other.getClass}")
    }
    new SbtIdeaPluginWizardStep(settingsStep, this, newProjectSettings, projectNameField)
  }

  override def createModule(moduleModel: ModifiableModuleModel): module.Module = {
    patchModuleNameAndFileName()
    super.createModule(moduleModel)
  }

  /**
   * Patch module name & module file name: set it to camel case because plugin name will also be camel-cased.<br>
   * Plugin name will be used as a new module name after sbt project reimport.<br>
   * So if we do not sync them now, there will be a notification that old module is removed and a new one is created
   */
  private def patchModuleNameAndFileName(): Unit = {
    this.setName(toCamelCase(this.getName))

    val oldModuleFilePath = this.getModuleFilePath
    val newModuleFilePath: String = if (oldModuleFilePath == null) null else {
      val file = new File(oldModuleFilePath)
      FileUtilRt.toSystemIndependentName(file.getParent) + "/" + toCamelCase(file.getName)
    }
    this.setModuleFilePath(newModuleFilePath)
  }

  def updateNewProjectSettings(settingsFromWizard: NewProjectSettings): Unit = {
    newProjectSettings = settingsFromWizard
  }

  override protected def processExtractedArchive(root: File): Unit = {
    assert(newProjectSettings != null, "new project settings not initialized")
    val projectValName  = toCamelCase(newProjectSettings.pluginName)
    val scalaVersion    = scala.util.Properties.versionNumberString
    val pluginName      = newProjectSettings.pluginName
    val pluginVendor    = newProjectSettings.pluginVendor
    val intelliJBuild   = newProjectSettings.intelliJBuildNumber
    val platformName    = newProjectSettings.intelliJPlatformKind.toString
    val pluginID        = toDotSeparatedId(pluginVendor + " " + pluginName)
    val sinceBuild      = newProjectSettings.intelliJBuildNumber.takeWhile(_ != '.') + ".0"
    val sbtIdeaVersion  = fetchLatestSbtIdeaVersion

    replaceInFile(root, "build.sbt", Map(
      "(^.+lazy\\s+val\\s+)(\\w+)(\\s+=.+$)"          -> projectValName,
      "scalaVersion".keyInitQuoted                    -> scalaVersion,
      "ThisBuild / intellijPluginName".keyInitQuoted  -> pluginName,
      "ThisBuild / intellijBuild".keyInitQuoted       -> intelliJBuild,
      "ThisBuild / intellijPlatform".keyInit          -> s"IntelliJPlatform.$platformName"
    ))

    replaceInFile(root, "resources/META-INF/plugin.xml", Map(
      "id".tagBody                            -> pluginID,
      "name".tagBody                          -> pluginName,
      "vendor".tagBody                        -> pluginVendor,
      "idea-version/since-build".emptyTagAttr -> sinceBuild
    ))

    replaceInFile(root, "project/plugins.sbt", Map(
      """(^.*addSbtPlugin\(\s*"org.jetbrains"\s*%\s*"sbt-idea-plugin"\s*%\s*")([^"]+)("\s*\).*$)""" -> sbtIdeaVersion
    ))

  }
}

object SbtIdeaPluginProjectBuilder {
  final case class NewProjectSettings(
    includeSamples: Boolean,
    pluginName: String,
    pluginVendor: String,
    intelliJBuildNumber: String,
    intelliJPlatformKind: SbtIdeaPluginPlatformKind
  )

  private def toDotSeparatedId(string: String): String = string.toLowerCase.replaceAll("\\s+", ".")

  private def toCamelCase(string: String, capitalizeFirst: Boolean = false): String = {
    var isPrevLowerCase = false
    var isNextUpperCase = capitalizeFirst
    val result = new StringBuilder
    for (i <- 0 until string.length) {
      val currentChar = string.charAt(i)
      if (!Character.isLetterOrDigit(currentChar)) isNextUpperCase = result.nonEmpty || isNextUpperCase
      else {
        result.append(if (isNextUpperCase) Character.toUpperCase(currentChar)
        else if (isPrevLowerCase) currentChar
        else Character.toLowerCase(currentChar))
        isNextUpperCase = false
      }
      isPrevLowerCase = result.nonEmpty && Character.isLowerCase(currentChar)
    }
    result.toString
  }

  private def fetchLatestSbtIdeaVersion: String = {
    import spray.json._
    import DefaultJsonProtocol._
    val versionURL = "https://api.github.com/repos/JetBrains/sbt-idea-plugin/tags?per_page=1"
    val defaultVersion = "3.8.4"
    extensions
      .withProgressSynchronously(DevkitBundle.message("sbtidea.version.fetch")) {
        Try(HttpConfigurable.getInstance().openHttpConnection(versionURL)).map { connection =>
          try {
            val json = Source.fromInputStream(connection.getInputStream).mkString.parseJson
            json match {
              case JsArray(JsObject(fields) +: _) if fields.nonEmpty =>
                fields.get("name").map(_.convertTo[String]).getOrElse(defaultVersion).dropWhile(!_.isDigit)
              case _ => defaultVersion
            }
          } finally { connection.disconnect() }
        }
      }.getOrElse(defaultVersion)
  }

}


