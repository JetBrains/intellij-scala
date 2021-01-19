package org.jetbrains.idea.devkit.scala.project

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SettingsStep}
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.idea.devkit.scala.DevkitBundle
import org.jetbrains.idea.devkit.scala.project.SbtIdeaPluginProjectBuilder.{NewProjectSettings, fetchLatestSbtIdeaVersion, toCamelCase, toDotSeparatedId}
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.sbt.project.template.ArchivedSbtProjectBuilder

import java.net.URL
import java.nio.file.Path
import scala.io.Source
import scala.util.Try

class SbtIdeaPluginProjectBuilder(archiveURL: URL) extends ArchivedSbtProjectBuilder(archiveURL) {

  private var newProjectSettings = NewProjectSettings(
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
    val sinceBuild      = newProjectSettings.intelliJBuildNumber.takeWhile(_ != '.') + ".*"
    val sbtIdeaVersion  = fetchLatestSbtIdeaVersion

    replaceInFile("build.sbt", Map(
      "$$PROJECT_VAL_NAME$$"  -> projectValName,
      "$$SCALA_VERSION$$"     -> scalaVersion,
      "$$PLUGIN_NAME$$"       -> pluginName,
      "$$INTELLIJ_BUILD$$"    -> intelliJBuild,
      "$$INTELLIJ_PLATFORM$$" -> platformName
    ))

    replaceInFile("src/main/resources/META-INF/plugin.xml", Map(
      "$$PLUGIN_ID$$"     -> pluginID,
      "$$PLUGIN_NAME$$"   -> pluginName,
      "$$PLUGIN_VENDOR$$" -> pluginVendor,
      "$$SINCE_BUILD$$"   -> sinceBuild
    ))

    replaceInFile("project/plugins.sbt", Map(
      "$$SBT_IDEA_PLUGIN_VERSION$$" -> sbtIdeaVersion
    ))

  }
}

object SbtIdeaPluginProjectBuilder {
  final case class NewProjectSettings(
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


