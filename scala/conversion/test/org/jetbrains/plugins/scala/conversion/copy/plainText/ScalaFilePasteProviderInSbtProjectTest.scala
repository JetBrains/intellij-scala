package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{ModuleRootManager, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.project._
import org.junit.Assert.assertTrue

/**
 * This test is a lightweight alternative of [[ScalaFilePasteProviderInSbtProjectExternalSystemIntegrationTest]].
 * It tests behavior of [[ScalaFilePasteProvider.calculatePasteActionOutcome]] more exhaustively,
 * without creating real, heavy-weight SBT projects.
 */
class ScalaFilePasteProviderInSbtProjectTest
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaFilePasteProviderInSbtProjectTestLike {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  private val PastedSimpleCodeWithAddSbtPlugin =
    """addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
      |addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.0")""".stripMargin

  private val PastedComplexCodeWithAddSbtPlugin =
    """//line comment
      |/*
      | block comment
      | */
      |/**
      | * Doc Comment
      | */
      |addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
      |
      |resolvers ++= Seq(
      |  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      |)
      |
      |libraryDependencies ++= Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.16" % Test
      |)""".stripMargin

  private val PastedCodeWithoutAddSbtPlugin =
    """//line comment
      |/*
      | block comment
      | */
      |/**
      | * Doc Comment
      | */
      |
      |resolvers ++= Seq(
      |  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      |)
      |
      |libraryDependencies ++= Seq(
      |  "org.scalatest" %% "scalatest" % "3.2.16" % Test
      |)""".stripMargin

  private var buildModuleSourceRoot: VirtualFile = _

  override protected def setUp(): Unit = {
    super.setUp()

    // Need to do it to trigger
    // org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProvider.shouldCreateOrUpdatePluginsSbtFile
    val module = getModule
    markModuleAsBuildModule(module)

    assertTrue("Module should be treated as a build module", module.isBuildModule)
    assertTrue("Module should have Scala in it", module.hasScala)

    buildModuleSourceRoot = ModuleRootManager.getInstance(getModule).getSourceRoots()(0)
  }

  private def markModuleAsBuildModule(module: Module): Unit = {
    // Change the module name by adding "-build" suffix
    // Currently it's the only way of marking build modules
    // See org.jetbrains.plugins.scala.project.ModuleExt#isBuildModule
    val newModuleName = module.getName + "-build"

    WriteAction.runAndWait { () =>
      val project = module.getProject

      val modifiableModel = project.modifiableModel
      modifiableModel.renameModule(module, newModuleName)
      modifiableModel.commit()

      ScalaModuleSettings.assignDummyModuleSettingsForTests(module, isBuildModule = true, ScalaLanguageLevel.Scala_2_12)

      // Need to invalidate caches as some module settings are cached during setUp
      // (e.g. org.jetbrains.plugins.scala.project.ModuleExt#scalaModuleSettings)
      ProjectRootManager.getInstance(project).incModificationCount()
    }
  }

  def testCreateNewPluginSbtFile(): Unit = {
    doPasteToDirectoryAndCreateNewFileTest(
      directory = buildModuleSourceRoot,
      pastedCode = PastedComplexCodeWithAddSbtPlugin,
      expectedNewFileName = "plugins.sbt"
    )
  }

  def testUpdateExistingPluginsSbtFile_SimpleCode_To_EmptyFile(): Unit = {
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """""",
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""$Caret$PastedSimpleCodeWithAddSbtPlugin"""
    )
  }

  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithPlugins(): Unit =
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |""".stripMargin
    )

  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithPlugins_MoreTrailingSpaces(): Unit =
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """
          |
          |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
          |
          |
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""
           |
           |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |
           |
           |""".stripMargin
    )

  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithPlugins_WithLeadingComments(): Unit = {
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """// some comment
          |/* nothing interesting up here */
          |
          |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""// some comment
           |/* nothing interesting up here */
           |
           |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |""".stripMargin
    )
  }

  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithPlugins_WithLeadingDefinitions(): Unit = {
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """// constants first
          |val scalafmtVersion = "2.5.5"
          |val scalaJsVer      = "1.12.0"
          |
          |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % scalaJsVer)
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""// constants first
           |val scalafmtVersion = "2.5.5"
           |val scalaJsVer      = "1.12.0"
           |
           |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % scalaJsVer)
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |""".stripMargin
    )
  }

  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithPlugins_WithLibraryDependencies(): Unit = {
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
          |
          |libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.13"
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |
           |libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.13"
           |""".stripMargin
    )
  }
  
  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithPlugins_WithLibraryDependenciesAndLeadingDefinitions(): Unit = {
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """// vals block
          |val scalafmt = "2.5.5"
          |
          |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
          |
          |// other settings
          |libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0"
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""// vals block
           |val scalafmt = "2.5.5"
           |
           |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |
           |// other settings
           |libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0"
           |""".stripMargin
    )
  }
  
  // ---------------------------------------------------------------------------
  // Pattern D: Resolvers before plugins tests
  // ---------------------------------------------------------------------------
  
  def testUpdateExistingPluginsSbtFile_SimpleCode_To_FileWithPlugins_WithResolvers(): Unit = {
    doPasteToDirectoryAndUpdateExistingFileTest(
      directory = buildModuleSourceRoot,
      existingFileName = "plugins.sbt",
      fileTextBefore =
        """// custom repositories first
          |resolvers ++= Seq(
          |  "Sonatype Releases"  at "https://oss.sonatype.org/content/repositories/releases",
          |  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
          |)
          |
          |// plugin block
          |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
          |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
          |
          |// and maybe more below …
          |""".stripMargin,
      pastedText = PastedSimpleCodeWithAddSbtPlugin,
      expectedFileTextAfter =
        s"""// custom repositories first
           |resolvers ++= Seq(
           |  "Sonatype Releases"  at "https://oss.sonatype.org/content/repositories/releases",
           |  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
           |)
           |
           |// plugin block
           |addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
           |addSbtPlugin("org.scala-js"   % "sbt-scalajs" % "1.12.0")
           |$Caret$PastedSimpleCodeWithAddSbtPlugin
           |
           |// and maybe more below …
           |""".stripMargin
    )
  }
}