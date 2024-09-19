package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.openapi.project.Project
import com.intellij.testFramework.FixtureRuleKt.useProject
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizardData.scalaBuildSystemData
import org.jetbrains.sbt.project.template.wizard.buildSystem.SbtScalaNewProjectWizardData.scalaSbtData
import org.jetbrains.sbt.project.template.wizard.buildSystem.ScalaGitNewProjectWizardData.scalaGitData
import org.jetbrains.sbt.project.template.wizard.buildSystem.ScalaSampleCodeNewProjectWizardData.scalaSampleCodeData

// TODO:
//  - test .gitignore creation
//  - check added sample code
//  - test with IntelliJ build system as well
class NewSbtProjectWizardTest extends NewScalaProjectWizardTestBase with ExactMatch {

  def testCreateProjectWithLowerCaseName(): Unit =
    runSimpleCreateSbtProjectTest("lowe_case_project_name")

  def testCreateProjectWithUpperCaseName(): Unit =
    runSimpleCreateSbtProjectTest("UpperCaseProjectName", packagePrefixOpt = Some("org.example.prefix"))

  //SCL-12528, SCL-12528
  def testCreateProjectWithDotsSpacesAndDashesInNameName(): Unit =
    runSimpleCreateSbtProjectTest("project_name_with_dots spaces and-dashes and UPPERCASE")

  private def runSimpleCreateSbtProjectTest(projectName: String, packagePrefixOpt: Option[String] = None): Unit = {
    val scalaVersion = "2.13.14"
    val sbtVersion = Versions.SBT.LatestSbtVersion

    //noinspection TypeAnnotation
    val expectedProject = new project(projectName) {
      lazy val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = false)(scalaVersion)

      libraries := scalaLibraries
      libraries.exactMatch()

      modules := Seq(
        new module(projectName) {
          libraryDependencies := scalaLibraries
          sources := Seq("src/main/scala")
          testSources := Seq("src/test/scala")
          excluded := Seq("target")
        },
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
      packagePrefixOpt
    )(expectedProject)
  }

  private def runCreateSbtProjectTest(
    projectName: String,
    scalaVersion: String,
    sbtVersion: String,
    packagePrefix: Option[String] = None
  )(
    expectedProject: project
  ): Unit = {
    val project = createScalaProject(NewProjectWizardConstants.Language.SCALA, projectName) { step =>
      scalaBuildSystemData(step).setBuildSystem(NewProjectWizardConstants.BuildSystem.SBT)

      val sbtData = scalaSbtData(step)
      sbtData.setScalaVersion(scalaVersion)
      sbtData.setSbtVersion(sbtVersion)
      sbtData.setPackagePrefix(packagePrefix.getOrElse(""))

      // TODO: test different values
      scalaSampleCodeData(step).setAddSampleCode(false)
      scalaGitData(step).setGit(false)
    }

    useProject(project, false, assertProjectsEqual(expectedProject, _: Project))
  }
}
