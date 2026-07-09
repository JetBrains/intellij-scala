package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.sbt.project.template.SbtModuleBuilder.IdeSettingsPluginVersion

/**
 * The test verifies generated SBT project files from the new-project wizard without running the full project.
 *
 * It's effectively a "Unit" test for  [[org.jetbrains.sbt.project.template.SbtModuleBuilder.createProjectTemplateIn]]
 *
 * For full-import and IntelliJ project-structure validation, see [[NewSbtProjectWizardFullImportTest]]
 */
class NewSbtProjectWizardGeneratedFilesTest extends NewSbtProjectWizardTestBase {
  import NewSbtProjectWizardGeneratedFilesTest.TestData.*

  def testScala3VersionsOlderThan3_3_0AreHidden(): Unit = {
    val versions = availableScalaVersionsFromSbtWizard

    assertUnsupportedScala3VersionsAreHidden(versions, ScalaVersion.Latest.Scala_3_3.withMinor(0))
  }

  def testCreateSbt_0_13_Project(): Unit = {
    runFileGenerationOnlyTest(
      config = sbt013ProjectConfig,
      expectedFiles = GeneratedProjectFilesExpectation(
        buildSbt =
          """scalaVersion in ThisBuild := "2.13.14"
            |
            |lazy val root = (project in file("."))
            |  .settings(
            |    name := "sbt0_13_project_template"
            |  )
            |""".stripMargin,
        buildProperties =
          s"""sbt.version = ${sbt013ProjectConfig.sbtVersion}
             |""".stripMargin,
        pluginsSbt = None,
      )
    )
  }

  def testCreateSbt_1_0_Project(): Unit = {
    runFileGenerationOnlyTest(
      config = sbt10ProjectConfig,
      expectedFiles = GeneratedProjectFilesExpectation(
        buildSbt =
          """scalaVersion in ThisBuild := "2.13.14"
            |
            |lazy val root = (project in file("."))
            |  .settings(
            |    name := "sbt1_0_project_template"
            |  )
            |""".stripMargin,
        buildProperties =
          s"""sbt.version = ${sbt10ProjectConfig.sbtVersion}
             |""".stripMargin,
        pluginsSbt = None,
      )
    )
  }

  def testCreateSbt_1_Latest_Project(): Unit = {
    runFileGenerationOnlyTest(
      config = sbt1LatestProjectConfig,
      expectedFiles = GeneratedProjectFilesExpectation(
        buildSbt =
          """ThisBuild / scalaVersion := "2.13.14"
            |
            |lazy val root = (project in file("."))
            |  .settings(
            |    name := "sbt1_project_template"
            |  )
            |""".stripMargin,
        buildProperties =
          s"""sbt.version = ${sbt1LatestProjectConfig.sbtVersion}
             |""".stripMargin,
        pluginsSbt = None,
      )
    )
  }

  def testCreateSbt_1_Latest_Project_WithPackagePrefix(): Unit = {
    runFileGenerationOnlyTest(
      config = sbt1LatestProjectWithPackagePrefixConfig,
      expectedFiles = GeneratedProjectFilesExpectation(
        buildSbt =
          """ThisBuild / scalaVersion := "2.13.14"
            |
            |lazy val root = (project in file("."))
            |  .settings(
            |    name := "sbt1_project_template_with_prefix",
            |    idePackagePrefix := Some("org.example.prefix")
            |  )
            |""".stripMargin,
        buildProperties =
          s"""sbt.version = ${sbt1LatestProjectWithPackagePrefixConfig.sbtVersion}
             |""".stripMargin,
        pluginsSbt = Some(s"""addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "$IdeSettingsPluginVersion")\n"""),
      )
    )
  }

  def testCreateSbt_2_Latest_Project(): Unit = {
    runFileGenerationOnlyTest(
      config = sbt2LatestProjectConfig,
      expectedFiles = GeneratedProjectFilesExpectation(
        buildSbt =
          """scalaVersion := "3.8.2"
            |
            |lazy val root = rootProject
            |  .settings(
            |    name := "sbt2_project_template",
            |    libraryDependencies ++= Seq(
            |      //You can add library dependencies here, for example,
            |      //"org.scalatest" %% "scalatest" % "3.2.19" % Test,
            |      //"org.scalameta" %% "munit" % "1.2.3" % Test
            |    )
            |  )
            |""".stripMargin,
        buildProperties =
          s"""sbt.version = ${sbt2LatestProjectConfig.sbtVersion}
             |""".stripMargin,
        pluginsSbt = None,
      )
    )
  }

  def testCreateSbt_2_Latest_Project_WithPackagePrefix(): Unit = {
    runFileGenerationOnlyTest(
      config = sbt2LatestProjectWithPackagePrefixConfig,
      expectedFiles = GeneratedProjectFilesExpectation(
        buildSbt =
          """scalaVersion := "3.8.2"
            |
            |lazy val root = rootProject
            |  .settings(
            |    name := "sbt2_project_template_with_prefix",
            |    idePackagePrefix := Some("org.example.prefix"),
            |    libraryDependencies ++= Seq(
            |      //You can add library dependencies here, for example,
            |      //"org.scalatest" %% "scalatest" % "3.2.19" % Test,
            |      //"org.scalameta" %% "munit" % "1.2.3" % Test
            |    )
            |  )
            |""".stripMargin,
        buildProperties =
          s"""sbt.version = ${sbt2LatestProjectWithPackagePrefixConfig.sbtVersion}
             |""".stripMargin,
        pluginsSbt = Some(s"""addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "$IdeSettingsPluginVersion")\n"""),
      )
    )
  }
}

object NewSbtProjectWizardGeneratedFilesTest {
  import NewSbtProjectWizardTestBase.SbtWizardProjectConfig
  import org.jetbrains.sbt.SbtVersion

  object TestData {
    val sbt013ProjectConfig: SbtWizardProjectConfig = SbtWizardProjectConfig(
      projectName = "sbt0_13_project_template",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_2_13, "14"),
      sbtVersion = SbtVersion.Latest.Sbt_0_13,
    )

    val sbt10ProjectConfig: SbtWizardProjectConfig = SbtWizardProjectConfig(
      projectName = "sbt1_0_project_template",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_2_13, "14"),
      sbtVersion = SbtVersion("1.0.4"),
    )

    val sbt1LatestProjectConfig: SbtWizardProjectConfig = SbtWizardProjectConfig(
      projectName = "sbt1_project_template",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_2_13, "14"),
      sbtVersion = SbtVersion.Latest.Sbt_1,
    )

    val sbt1LatestProjectWithPackagePrefixConfig: SbtWizardProjectConfig = SbtWizardProjectConfig(
      projectName = "sbt1_project_template_with_prefix",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_2_13, "14"),
      sbtVersion = SbtVersion.Latest.Sbt_1,
      packagePrefix = Some("org.example.prefix"),
    )

    val sbt2LatestProjectConfig: SbtWizardProjectConfig = SbtWizardProjectConfig(
      projectName = "sbt2_project_template",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_3_8, "2"),
      sbtVersion = SbtVersion.Latest.Sbt_2,
    )

    val sbt2LatestProjectWithPackagePrefixConfig: SbtWizardProjectConfig = SbtWizardProjectConfig(
      projectName = "sbt2_project_template_with_prefix",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_3_8, "2"),
      sbtVersion = SbtVersion.Latest.Sbt_2,
      packagePrefix = Some("org.example.prefix"),
    )
  }
}
