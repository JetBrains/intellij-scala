package org.jetbrains.sbt.project.template

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.{ApiStatus, NonNls}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.template.DefaultModuleContentEntryFolders
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.sbt.Sbt

import java.nio.file.{FileAlreadyExistsException, Files, Path}
import javax.swing._

/**
 * Do not extend, it will be made final in the future.<br>
 * Consider using [[SbtModuleBuilderBase]] instead
 *
 * @param _selections initial selections value<br>
 *                    The parameter value is copied copied, changes to the original object do not effect the builder
 */
@ApiStatus.Internal
class SbtModuleBuilder(
  _selections: SbtModuleBuilderSelections
) extends SbtModuleBuilderBase {

  private val selections = _selections.copy() // Selections is mutable data structure
  def this() = this(SbtModuleBuilderSelections.default)

  override def getNodeIcon: Icon = Sbt.Icon

  override def setupModule(module: Module): Unit = {
    val settings = getExternalProjectSettings
    settings.setResolveClassifiers(selections.downloadScalaSdkSources)
    settings.setResolveSbtClassifiers(selections.downloadSbtSources)

    super.setupModule(module)
  }

  override protected def createProjectTemplateIn(root: Path): Option[DefaultModuleContentEntryFolders] = {
    val name = getName
    val sbtVersion = selections.sbtVersion.map(_.minor).getOrElse(Versions.SBT.LatestSbtVersion)
    val scalaVersion = selections.scalaVersion.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)
    val packagePrefix = selections.packagePrefix

    SbtModuleBuilder.createProjectTemplateIn(root, name, scalaVersion, sbtVersion, packagePrefix)
  }
}

object SbtModuleBuilder {
  private def createProjectTemplateIn(
    root: Path,
    @NonNls name: String,
    @NonNls scalaVersion: String,
    @NonNls sbtVersion: String,
    packagePrefix: Option[String]
  ): Option[DefaultModuleContentEntryFolders] = {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory

    if (createNewFile(buildFile) && mkdir(projectDir)) {
      val mainSourcesPath = "src/main/scala"
      val testSourcesPath = "src/test/scala"

      mkdirs(root / mainSourcesPath)
      mkdirs(root / testSourcesPath)

      val rootProjectSettings: Seq[String] = Seq(
        s"""name := "$name""""
      ) ++ packagePrefix.map { p =>
        s"""idePackagePrefix := Some("$p")""".stripMargin
      }

      val version = """0.1.0-SNAPSHOT"""

      // Slash syntax was introduced in sbt 1.1 (https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html)
      val isAtLeastSbt_1_1 = Version(sbtVersion) >= Version("1.1")
      val buildSbtBaseContent = if (isAtLeastSbt_1_1)
        s"""ThisBuild / version := "$version"
           |
           |ThisBuild / scalaVersion := "$scalaVersion""""
      else
        s"""version in ThisBuild := "$version"
           |
           |scalaVersion in ThisBuild := "$scalaVersion""""

      val indent = "    "
      val buildSbtContent =
        s"""$buildSbtBaseContent
           |
           |lazy val root = (project in file("."))
           |  .settings(
           |$indent${rootProjectSettings.mkString("", s",\n$indent", "")}
           |  )
           |""".stripMargin

      val buildPropertiesContent = s"""sbt.version = $sbtVersion"""

      val pluginsSbtContent = """addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.4")"""

      def ensureSingleNewLineAfter(text: String): String = text.stripTrailing() + "\n"

      Files.writeString(buildFile, ensureSingleNewLineAfter(buildSbtContent))
      Files.writeString(projectDir / Sbt.PropertiesFile, ensureSingleNewLineAfter(buildPropertiesContent))

      if (packagePrefix.isDefined) {
        Files.writeString(projectDir / Sbt.PluginsFile, ensureSingleNewLineAfter(pluginsSbtContent))
      }

      Some(DefaultModuleContentEntryFolders(
        Seq(mainSourcesPath),
        Seq(testSourcesPath),
        Nil,
        Nil,
        DefaultModuleContentEntryFolders.SbtRootTargets
      ))
    }
    else None
  }

  private def createNewFile(path: Path): Boolean =
    pathOp(path)(Files.createFile(_))

  private def mkdir(path: Path): Boolean =
    pathOp(path)(Files.createDirectory(_))

  private def mkdirs(path: Path): Boolean =
    pathOp(path)(Files.createDirectories(_))

  private def pathOp(path: Path)(op: Path => Unit): Boolean =
    try {
      op(path)
      true
    } catch {
      case _: FileAlreadyExistsException => false
    }
}