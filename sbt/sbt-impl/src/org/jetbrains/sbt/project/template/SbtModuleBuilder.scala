package org.jetbrains.sbt.project.template

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.{ApiStatus, NonNls}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.DefaultModuleContentEntryFolders
import org.jetbrains.sbt.{Sbt, SbtVersion}

import java.nio.file.{FileAlreadyExistsException, Files, Path}
import javax.swing.*

/**
 * Do not extend, it will be made final in the future.<br>
 * Consider using [[SbtModuleBuilderBase]] instead
 *
 * @param _selections initial selections value<br>
 *                    The parameter value is copied, changes to the original object do not affect the builder
 */
@ApiStatus.Internal
class SbtModuleBuilder(
  _selections: SbtModuleBuilderSelections
) extends SbtModuleBuilderBase {

  private val selections = _selections.copy() // Selections are mutable data structure
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
    val sbtVersion = selections.sbtVersion.getOrElse(SbtVersion(Versions.SBT.LatestSbtVersion))
    val scalaVersion = selections.scalaVersion.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)
    val packagePrefix = selections.packagePrefix

    SbtModuleBuilder.createProjectTemplateIn(
      root = root,
      name = name,
      scalaVersion = scalaVersion,
      sbtVersion = sbtVersion,
      packagePrefix = packagePrefix,
      // TODO: add library dependencies example for all sbt versions? What about the versions inside?
      addLibraryDependenciesExample = sbtVersion.isSbt2
    )
  }
}

object SbtModuleBuilder {

  @NonNls val IdeSettingsPluginVersion = "1.1.4"

  private case class ProjectTemplateFileContents(
    `build.sbt`: String,
    `build.properties`: String,
    `plugins.sbt`: Option[String]
  )

  private def createProjectTemplateIn(
    root: Path,
    @NonNls name: String,
    @NonNls scalaVersion: String,
    sbtVersion: SbtVersion,
    packagePrefix: Option[String],
    addLibraryDependenciesExample: Boolean
  ): Option[DefaultModuleContentEntryFolders] = {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory

    import FileUtils.*

    if (createNewFile(buildFile) && mkdir(projectDir)) {
      val mainSourcesPath = "src/main/scala"
      val testSourcesPath = "src/test/scala"

      mkdirs(root / mainSourcesPath)
      mkdirs(root / testSourcesPath)

      val fileContents = buildProjectTemplateFileContents(
        sbtVersion = sbtVersion,
        scalaVersion = scalaVersion,
        name = name,
        packagePrefix = packagePrefix,
        addLibraryDependenciesExample = addLibraryDependenciesExample,
      )

      writeProjectTemplateFiles(buildFile, projectDir, fileContents)

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

  private def buildRootProjectSbtSettings(name: String, packagePrefix: Option[String], addLibraryDependenciesExample: Boolean) = {
    val nameSetting = Seq(
      s"""name := "$name""""
    )
    val packagePrefixSetting = packagePrefix.map { p =>
      s"""idePackagePrefix := Some("$p")""".stripMargin
    }
    nameSetting ++
      packagePrefixSetting ++
      addLibraryDependenciesExample.option(LibraryDependenciesSetting).toSeq
  }

  private val LibraryDependenciesSetting: String =
    """libraryDependencies ++= Seq(
      |  //You can add library dependencies here, for example,
      |  //"org.scalatest" %% "scalatest" % "3.2.19" % Test,
      |  //"org.scalameta" %% "munit" % "1.2.3" % Test
      |)""".stripMargin

  private def buildSbtContentForSbt2(
    @NonNls scalaVersion: String,
    rootProjectSettings: Seq[String]
  ): String = {
    val indentedSettings = formatRootProjectSettings(rootProjectSettings)

    s"""scalaVersion := "$scalaVersion"
       |
       |lazy val root = rootProject
       |  .settings(
       |$indentedSettings
       |  )
       |""".stripMargin
  }

  private def buildSbtContentForSbt1_After_1_1(
    @NonNls scalaVersion: String,
    rootProjectSettings: Seq[String]
  ): String = {
    //TODO: don't add "version" in all sbt versions, not only in SBT 2
    val rootProjectSettingsText = formatRootProjectSettings(rootProjectSettings)

    s"""ThisBuild / scalaVersion := "$scalaVersion"
       |
       |lazy val root = (project in file("."))
       |  .settings(
       |$rootProjectSettingsText
       |  )
       |""".stripMargin
  }

  /**
   * Uses `in` syntax instead of slash `/` syntax.
   * Slash syntax was introduced only in sbt 1.1
   *
   * @see https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html
   */
  private def buildSbtContentForSbt1_Before_1_1(
    @NonNls scalaVersion: String,
    rootProjectSettings: Seq[String]
  ): String = {
    val rootProjectSettingsText = formatRootProjectSettings(rootProjectSettings)

    s"""scalaVersion in ThisBuild := "$scalaVersion"
       |
       |lazy val root = (project in file("."))
       |  .settings(
       |$rootProjectSettingsText
       |  )
       |""".stripMargin
  }

  private def formatRootProjectSettings(settings: Seq[String]): String =
    settings.mkString(",\n").indented(4)

  private def buildProjectTemplateFileContents(
    sbtVersion: SbtVersion,
    scalaVersion: String,
    name: String,
    packagePrefix: Option[String],
    addLibraryDependenciesExample: Boolean,
  ): ProjectTemplateFileContents = {
    val rootProjectSettings: Seq[String] = buildRootProjectSbtSettings(
      name,
      packagePrefix,
      addLibraryDependenciesExample
    )
    val buildSbtFileContent: String =
      if (sbtVersion.isSbt2)
        buildSbtContentForSbt2(scalaVersion, rootProjectSettings)
      else if (sbtVersion >= SbtVersion("1.1"))
        buildSbtContentForSbt1_After_1_1(scalaVersion, rootProjectSettings)
      else
        buildSbtContentForSbt1_Before_1_1(scalaVersion, rootProjectSettings)

    val buildPropertiesFileContent: String =
      s"""sbt.version = ${sbtVersion.minor}"""

    val pluginsSbtFileContent: Option[String] =
      packagePrefix.isDefined.option(
        s"""addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "$IdeSettingsPluginVersion")"""
      )

    ProjectTemplateFileContents(
      `build.sbt` = buildSbtFileContent,
      `build.properties` = buildPropertiesFileContent,
      `plugins.sbt` = pluginsSbtFileContent
    )
  }

  private def writeProjectTemplateFiles(
    buildFile: Path,
    projectDir: Path,
    fileContents: ProjectTemplateFileContents
  ): Unit = {
    Files.writeString(buildFile, fileContents.`build.sbt`.ensureSingleNewLineAfter)
    Files.writeString(projectDir / Sbt.PropertiesFile, fileContents.`build.properties`.ensureSingleNewLineAfter)

    fileContents.`plugins.sbt`.foreach { pluginsSbt =>
      Files.writeString(projectDir / Sbt.PluginsFile, pluginsSbt.ensureSingleNewLineAfter)
    }
  }


  private implicit class StringAnyOps(private val text: String) extends AnyVal {
     def indented(spaces: Int): String = {
      val indent = " " * spaces
      indent + text.replace("\n", "\n" + indent)
    }

     def ensureSingleNewLineAfter: String = text.stripTrailing() + "\n"
  }

  private object FileUtils {
     def createNewFile(path: Path): Boolean =
      pathOp(path)(Files.createFile(_))

     def mkdir(path: Path): Boolean =
      pathOp(path)(Files.createDirectory(_))

     def mkdirs(path: Path): Boolean =
      pathOp(path)(Files.createDirectories(_))

     def pathOp(path: Path)(op: Path => Unit): Boolean =
      try {
        op(path)
        true
      } catch {
        case _: FileAlreadyExistsException => false
      }
  }
}
