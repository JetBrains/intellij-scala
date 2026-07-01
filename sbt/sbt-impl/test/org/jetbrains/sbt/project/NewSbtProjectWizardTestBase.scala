package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.openapi.project.Project
import com.intellij.testFramework.FixtureRuleKt.useProject
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizardData.scalaBuildSystemData
import org.jetbrains.sbt.project.template.wizard.buildSystem.SbtScalaNewProjectWizardData.scalaSbtData
import org.jetbrains.sbt.project.template.wizard.buildSystem.ScalaSampleCodeNewProjectWizardData.scalaSampleCodeData
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull}

import java.nio.file.{Files, Path}

abstract class NewSbtProjectWizardTestBase extends NewScalaProjectWizardTestBase {
  import NewSbtProjectWizardTestBase.SbtWizardProjectConfig

  protected final def availableScalaVersionsFromSbtWizard: Seq[String] =
    availableScalaVersionsFromWizard(
      NewProjectWizardConstants.Language.SCALA,
      "sbt_project_template_versions",
      checkJDK = false
    ) { step =>
      scalaBuildSystemData(step).setBuildSystem(NewProjectWizardConstants.BuildSystem.SBT)

      val sbtData = scalaSbtData(step)
      sbtData.setRunImportAfterProjectCreation(false)
      scalaSampleCodeData(step).setAddSampleCode(false)
      sbtData.availableScalaVersions
    }

  protected final case class GeneratedProjectFilesExpectation(
    buildSbt: String,
    buildProperties: String,
    pluginsSbt: Option[String],
  )

  protected final def runImportEnabledTest(config: SbtWizardProjectConfig)(assertProject: Project => Unit): Unit = {
    val project = createConfiguredProject(config, runImportAfterProjectCreation = true, checkJDK = true)
    useProject(project, false, (project: Project) => {
      assertProject(project)
      assertIndentationBasedSyntax(project, config.useIndentationBasedSyntax)
    })
  }

  protected final def runFileGenerationOnlyTest(
    config: SbtWizardProjectConfig,
    expectedFiles: GeneratedProjectFilesExpectation,
  ): Unit = {
    val project = createConfiguredProject(config, runImportAfterProjectCreation = false, checkJDK = false)
    useProject(project, false, (project: Project) => {
      assertGeneratedProjectFiles(project, expectedFiles)
      assertIndentationBasedSyntax(project, config.useIndentationBasedSyntax)
    })
  }

  protected final def assertIndentationBasedSyntax(project: Project, expected: Boolean): Unit =
    assertEquals(
      "The 'Use indentation-based syntax' setting was not configured correctly",
      expected,
      ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX
    )

  private def createConfiguredProject(
    config: SbtWizardProjectConfig,
    runImportAfterProjectCreation: Boolean,
    checkJDK: Boolean,
  ): Project =
    createScalaProject(
      NewProjectWizardConstants.Language.SCALA,
      config.projectName,
      checkJDK = checkJDK
    ) { step =>
      scalaBuildSystemData(step).setBuildSystem(NewProjectWizardConstants.BuildSystem.SBT)

      val sbtData = scalaSbtData(step)
      sbtData.setScalaVersion(config.scalaVersion.minor)
      sbtData.setSbtVersion(config.sbtVersion.minor)
      sbtData.setPackagePrefix(config.packagePrefix.getOrElse(""))
      sbtData.setUseIndentationBasedSyntax(config.useIndentationBasedSyntax)
      sbtData.setRunImportAfterProjectCreation(runImportAfterProjectCreation)

      // TODO: test different values
      scalaSampleCodeData(step).setAddSampleCode(false)
    }

  private def assertGeneratedProjectFiles(
    project: Project,
    expectedFiles: GeneratedProjectFilesExpectation,
  ): Unit = {
    val files = resolveProjectFiles(project)

    assertEquals(expectedFiles.buildSbt, Files.readString(files.buildSbt))
    assertEquals(expectedFiles.buildProperties, Files.readString(files.buildProperties))

    expectedFiles.pluginsSbt match {
      case Some(content) =>
        assertEquals(content, Files.readString(files.pluginsSbt))
      case None =>
        assertFalse("project/plugins.sbt should not be generated when package prefix is not configured", Files.exists(files.pluginsSbt))
    }
  }

  private case class ProjectFiles(
    buildSbt: Path,
    buildProperties: Path,
    pluginsSbt: Path,
  )

  private def resolveProjectFiles(project: Project): ProjectFiles = {
    val basePath = project.getBasePath
    assertNotNull("project base path should be set", basePath)

    val root = Path.of(basePath)
    ProjectFiles(
      buildSbt = root.resolve("build.sbt"),
      buildProperties = root.resolve("project").resolve("build.properties"),
      pluginsSbt = root.resolve("project").resolve("plugins.sbt"),
    )
  }
}

object NewSbtProjectWizardTestBase {

  final case class SbtWizardProjectConfig(
    projectName: String,
    scalaVersion: ScalaVersion,
    sbtVersion: SbtVersion,
    packagePrefix: Option[String] = None,
    useIndentationBasedSyntax: Boolean = false,
  )
}
