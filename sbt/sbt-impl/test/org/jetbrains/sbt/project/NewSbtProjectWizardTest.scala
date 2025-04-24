package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.openapi.project.Project
import com.intellij.testFramework.FixtureRuleKt.useProject
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizardData.scalaBuildSystemData
import org.jetbrains.sbt.project.template.wizard.buildSystem.SbtScalaNewProjectWizardData.scalaSbtData
import org.jetbrains.sbt.project.template.wizard.buildSystem.ScalaSampleCodeNewProjectWizardData.scalaSampleCodeData

// TODO:
//  - test .gitignore creation
//  - check added sample code
//  - test with IntelliJ build system as well
class NewSbtProjectWizardTest extends NewScalaProjectWizardTestBase with ExactMatch {

  def testCreateProjectWithLowerCaseName(): Unit =
    runSimpleCreateSbtProjectTest(projectName = "lower_case_project_name", scalaVersion = "2.13.14")

  def testCreateProjectWithUpperCaseName(): Unit =
    runSimpleCreateSbtProjectTest(projectName = "UpperCaseProjectName", scalaVersion = "2.13.14", packagePrefixOpt = Some("org.example.prefix"))

  //SCL-12528, SCL-12528
  def testCreateProjectWithDotsSpacesAndDashesInNameName(): Unit =
    runSimpleCreateSbtProjectTest(projectName = "project_name_with_dots spaces and-dashes and UPPERCASE", scalaVersion = "2.13.14")

  def testCreateScala3ProjectAndUseIndentationBasedSyntax(): Unit = {
    runSimpleCreateSbtProjectTest(projectName = "scala3-indentation-based-syntax", scalaVersion = "3.3.3", useIndentationBasedSyntax = true)
  }

  private def runSimpleCreateSbtProjectTest(projectName: String, scalaVersion: String, packagePrefixOpt: Option[String] = None, useIndentationBasedSyntax: Boolean = false): Unit = {
    val sbtVersion = Versions.SBT.LatestSbtVersion

    //noinspection TypeAnnotation
    val expectedProject = new project(projectName) {
      lazy val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = false)(scalaVersion)

      libraries := scalaLibraries
      libraries.exactMatch()

      lazy val mainModule = new module(s"$projectName.main") {
        libraryDependencies := scalaLibraries
        sources := Seq("scala")
      }

      lazy val testModule = new module(s"$projectName.test") {
        libraryDependencies := scalaLibraries
        testSources := Seq("scala")
        moduleDependencies += new dependency(mainModule) { isExported := false }
      }

      modules := Seq(
        new module(projectName) {
          excluded := Seq("target")
          moduleDependencies:= Seq(
            new dependency(mainModule) { isExported := false },
            new dependency(testModule) { isExported := false }
          )
        },
        mainModule, testModule,
        new module(s"$projectName.$projectName-build") {
          // TODO: why `-build` module contains empty string? in UI the `project` folder is marked as `sources`.
          //  Is it some implicit IntelliJ behaviour?
          sources := Seq("")
          excluded := Seq("project/target", "target")
        }
      )

      packagePrefixOpt.foreach { prefix =>
        packagePrefix := prefix
      }
    }

    runCreateSbtProjectTest(
      projectName,
      scalaVersion,
      sbtVersion,
      packagePrefixOpt,
      useIndentationBasedSyntax
    )(expectedProject)
  }

  private def runCreateSbtProjectTest(
    projectName: String,
    scalaVersion: String,
    sbtVersion: String,
    packagePrefix: Option[String],
    useIndentationBasedSyntax: Boolean
  )(
    expectedProject: project
  ): Unit = {
    val project = createScalaProject(NewProjectWizardConstants.Language.SCALA, projectName) { step =>
      scalaBuildSystemData(step).setBuildSystem(NewProjectWizardConstants.BuildSystem.SBT)

      val sbtData = scalaSbtData(step)
      sbtData.setScalaVersion(scalaVersion)
      sbtData.setSbtVersion(sbtVersion)
      sbtData.setPackagePrefix(packagePrefix.getOrElse(""))
      sbtData.setUseIndentationBasedSyntax(useIndentationBasedSyntax)

      // TODO: test different values
      scalaSampleCodeData(step).setAddSampleCode(false)
    }

    useProject(project, false, (project: Project) => {
      assertProjectsEqual(expectedProject, project, singleContentRootModules = false)
      junit.framework.TestCase.assertEquals(
        "The 'Use indentation-based syntax' setting was not configured correctly",
        useIndentationBasedSyntax,
        ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX
      )
    })
  }
}
