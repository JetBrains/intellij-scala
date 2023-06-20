package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.sbt.project.ProjectStructureDsl._
import org.jetbrains.sbt.project.template.SbtModuleBuilder

class NewSbtProjectWizardTest extends NewScalaProjectWizardTestBase with ExactMatch {

  override protected def setUp(): Unit = {
    super.setUp()
    configureJdk()
  }

  def testCreateProjectWithLowerCaseName(): Unit =
    runSimpleCreateSbtProjectTest("lowe_case_project_name")

  def testCreateProjectWithUpperCaseName(): Unit =
    runSimpleCreateSbtProjectTest("UpperCaseProjectName", packagePrefixOpt = Some("org.example.prefix"))
  
  //SCL-12528, SCL-12528
  def testCreateProjectWithDotsSpacesAndDashesInNameName(): Unit =
    runSimpleCreateSbtProjectTest("project.name.with.dots spaces and-dashes and UPPERCASE")

  private def runSimpleCreateSbtProjectTest(projectName: String, packagePrefixOpt: Option[String] = None): Unit = {
    val scalaVersion = "2.13.6"
    val sbtVersion = Versions.SBT.LatestSbtVersion

    //noinspection TypeAnnotation
    val expectedProject = new project(projectName) {
      lazy val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(scalaVersion)

      libraries := scalaLibraries
      libraries.exactMatch()

      modules := Seq(
        new module(projectName) {
          excluded := Seq("target")
        },
        new module(s"$projectName.main") {
          libraryDependencies := scalaLibraries
          sources := Seq("scala")
        },
        new module(s"$projectName.test") {
          libraryDependencies := scalaLibraries
          testSources := Seq("scala")
        },
        new module(s"$projectName-build") {
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
    val project: Project = createScalaProject(
      SbtProjectSystem.Id.getReadableName,
      projectName
    ) {
      case projectSettingsStep: ProjectSettingsStep =>
        val settingsStep = projectSettingsStep.getSettingsStepTyped[SbtModuleBuilder.Step]
        settingsStep.setScalaVersion(scalaVersion)
        settingsStep.setSbtVersion(sbtVersion)
        settingsStep.setPackagePrefix(packagePrefix.getOrElse(""))
      case _ =>
    }

    assertProjectsEqual(expectedProject, project)
  }
}
