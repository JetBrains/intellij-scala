package org.jetbrains.idea.devkit.scala.project

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SettingsStep}
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.idea.devkit.scala.DevkitBundle
import org.jetbrains.idea.devkit.scala.project.SbtIdeaPluginProjectBuilder.{NewProjectSettings, fetchLatestSbtIdeaVersion, toCamelCase, toDotSeparatedId}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.sbt.project.template.AbstractArchivedSbtProjectBuilder
import org.jetbrains.sbt.project.template.AbstractArchivedSbtProjectBuilder.SbtPatternExt

import java.io.File
import java.net.URL
import java.nio.file.Path
import scala.io.Source
import scala.util.Try

class SbtIdeaPluginProjectBuilder extends AbstractArchivedSbtProjectBuilder {

  private val LOG = Logger.getInstance(getClass)

  private val GH_WITH_EXAMPLES_URL  = "https://github.com/JetBrains/sbt-idea-example/archive/examples.zip"
  private val GH_NO_EXAMPLES_URL    = "https://github.com/JetBrains/sbt-idea-example/archive/noexamples.zip"
  private def FALLBACK_URL          = getClass.getClassLoader.getResource("sbt-idea-example.zip")

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
    "Default Name",
    "Default Vendor",
    Try(ApplicationInfo.getInstance().getBuild.withoutProductCode().asString()).getOrElse("LATEST-EAP-SNAPSHOT"),
    SbtIdeaPluginPlatformKind.IdeaCommunity)

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    new SbtIdeaPluginWizardStep(settingsStep, this, newProjectSettings)
  }

  def updateNewProjectSettings(settingsFromWizard: NewProjectSettings): Unit = {
    newProjectSettings = settingsFromWizard
  }

  override protected def processExtractedArchive(extractedPath: Path): Unit = {
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

    replaceInFile("build.sbt", Map(
      "(^.+lazy\\s+val\\s+)(\\w+)(\\s+=.+$)"          -> projectValName,
      "scalaVersion".keyInitQuoted                    -> scalaVersion,
      "ThisBuild / intellijPluginName".keyInitQuoted  -> pluginName,
      "ThisBuild / intellijBuild".keyInitQuoted       -> intelliJBuild,
      "ThisBuild / intellijPlatform".keyInit          -> s"IntelliJPlatform.$platformName"
    ))

    replaceInFile("resources/META-INF/plugin.xml", Map(
      "id".tagBody                            -> pluginID,
      "name".tagBody                          -> pluginName,
      "vendor".tagBody                        -> pluginVendor,
      "idea-version/since-build".emptyTagAttr -> sinceBuild
    ))

    replaceInFile("project/plugins.sbt", Map(
      """(^.*addSbtPlugin\(\s*"org.jetbrains"\s*%\s*"sbt-idea-plugin"\s*%\s*")([^"]+)("\s*\))""" -> sbtIdeaVersion
    ))

  }
}

object SbtIdeaPluginProjectBuilder {
  final case class NewProjectSettings(
                                       includeSamples: Boolean,
                                       pluginName: String,
                                       pluginVendor: String,
                                       intelliJBuildNumber: String,
                                       intelliJPlatformKind: SbtIdeaPluginPlatformKind)

  def toDotSeparatedId(string: String): String = string.toLowerCase.replaceAll("\\s+", ".")

  def toCamelCase(string: String, capitalizeFirst: Boolean = false): String = {
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

  def fetchLatestSbtIdeaVersion: String = {
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


