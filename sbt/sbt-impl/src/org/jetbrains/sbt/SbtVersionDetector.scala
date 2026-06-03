package org.jetbrains.sbt

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.project.settings.SbtExecutionSettings

import java.io.BufferedInputStream
import java.nio.file.{FileSystems, Files, Path}
import java.util.Properties
import scala.util.Using

object SbtVersionDetector {

  def detectSbtVersion(project: Project, externalProjectPath: String): SbtVersion = {
    val settings = SbtExternalSystemManager.executionSettingsFor(project, externalProjectPath)
    detectSbtVersion(settings)
  }

  def detectSbtVersion(project: Project): SbtVersion = {
    val settings = SbtExternalSystemManager.executionSettingsFor(project)
    detectSbtVersion(settings)
  }

  private def detectSbtVersion(settings: SbtExecutionSettings): SbtVersion = {
    val projectBaseDir = Path.of(settings.realProjectPath)
    val launcher = SbtUtil.getLauncherJar(settings)
    detectSbtVersion(projectBaseDir, launcher)
  }

  /**
   * The version of sbt defined in `project/build.properties` or fallback to the sbt version corresponding to launcher
   */
  def detectSbtVersion(projectRoot: Path, sbtLauncher: => Path): SbtVersion = {
    val fromProject = sbtVersionInProjectPropertiesFile(projectRoot)
    val fromProjectOrLauncher = fromProject.orElse(sbtVersionInBootPropertiesOfSbtLauncher(sbtLauncher)).map(SbtVersion(_))
    fromProjectOrLauncher.getOrElse(SbtVersion.Latest.Sbt_1)
  }

  /**
   * The version of sbt defined in `project/build.properties` in project root
   */
  def detectSbtVersionFromProjectProperties(projectRoot: Path): Option[SbtVersion] =
    sbtVersionInProjectPropertiesFile(projectRoot).map(SbtVersion(_))

  /**
   * Reads the sbt version from the `project/build.properties` file.<br>
   * File content example: {{{
   *   sbt.version = 1.10.7
   * }}}
   */
  private def sbtVersionInProjectPropertiesFile(projectDir: Path): Option[String] = {
    val projectPropertiesFile = projectDir / Sbt.ProjectDirectory / Sbt.PropertiesFile
    if (Files.exists(projectPropertiesFile))
      readPropertyFrom(projectPropertiesFile, "sbt.version")
    else
      None
  }

  //noinspection SameParameterValue
  private def readPropertyFrom(propertiesFilePath: Path, name: String): Option[String] = {
    Using.resource(new BufferedInputStream(Files.newInputStream(propertiesFilePath))) { input =>
    val properties = new Properties()
      properties.load(input)
      Option(properties.getProperty(name))
    }
  }

  // Examples: 1.10.7, 1.10.7-M1, 1.10.7-RC2
  private val BootFileVersionInLineRegex = """\d+(\.\d+)*(-\w+)?""".r

  /**
   * Reads sbt version from the `sbt-launch.jar/sbt/sbt.boot.properties` file.<br>
   * File content example: {{{
   *   [app]
   *     name: sbt
   *     version: ${sbt.version-read(sbt.version)[1.10.7]}
   *   ...
   * }}}
   */
  private def sbtVersionInBootPropertiesOfSbtLauncher(launcherJar: Path): Option[String] = {
    val appProperties = readSectionFromBootPropertiesOf(launcherJar, sectionName = "app")
    if (appProperties.get("name").contains("sbt")) {
      for {
        versionStr <- appProperties.get("version")
        version <- BootFileVersionInLineRegex.findFirstIn(versionStr)
      } yield version
    }
    else None
  }

  //noinspection SameParameterValue
  private def readSectionFromBootPropertiesOf(launcherJar: Path, sectionName: String): Map[String, String] = {
    Using.resource(FileSystems.newFileSystem(launcherJar)) { fs =>
      val bootPropertiesPath = fs.getPath("sbt/sbt.boot.properties")
      if (Files.exists(bootPropertiesPath)) {
        val lines = Files.lines(bootPropertiesPath).toArray.map(_.toString).toSeq
        readSectionFromBootPropertiesFileContent(lines, sectionName)
      } else {
        Map.empty[String, String]
      }
    }
  }

  private def readSectionFromBootPropertiesFileContent(fileLines: Seq[String], sectionName: String): Map[String, String] = {
    val sectionLines = fileLines
      .dropWhile(_.trim != s"[$sectionName]").drop(1)
      .takeWhile(!_.trim.startsWith("["))
    sectionLines.flatMap(findBootFileProperty).toMap
  }

  private val BootFilePropertyRegex = "^\\s*(\\w+)\\s*:(.+)".r.unanchored

  private def findBootFileProperty(line: String): Option[(String, String)] =
    line match {
      case BootFilePropertyRegex(name, value) => Some((name, value.trim))
      case _ => None
    }
}
