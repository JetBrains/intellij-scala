package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.sbt.project.ProjectStructureDsl.{excluded, libraries, libraryDependencies, module, modules, project, sources, testSources, _}
import org.jetbrains.sbt.project.template.techhub.{TechHubModuleBuilder, TechHubProjectTemplate}

class NewLightbendStarterProjectWizardTest extends NewScalaProjectWizardTestBase with ExactMatch {

  def testCreateLightbendStarterProject_LowerCaseNameWithDashes(): Unit =
    runCreateLightbendStarterProjectTest("lower-case-name-with-dashes")

  def testCreateLightbendStarterProject_LowerCaseName(): Unit =
    runCreateLightbendStarterProjectTest("lower_case_name")

  def testCreateLightbendStarterProject_UpperCaseName(): Unit =
    runCreateLightbendStarterProjectTest("UpperCaseName")

  private def runCreateLightbendStarterProjectTest(
    projectName: String
  ): Unit = {
    // NOTE: the scala version can change if the template is modified
    val scalaVersion = ScalaVersion.fromString("2.13.6").get
    val templateName = "Play Scala Seed"

    //noinspection TypeAnnotation
    val expectedProject = new project(projectName) {
      lazy val scalaLibrary = expectedScalaLibrary(scalaVersion)
      lazy val playLibrary = new library("sbt: com.typesafe.play:build-link:2.8.8:jar")

      // NOTE: actually there are much more libraries in the dependencies but we health-check just a single one
      lazy val myLibraries = Seq(scalaLibrary, playLibrary)

      libraries ++= myLibraries
      libraries.inexactMatch() // too many other libraries to check in this project template
      modules := Seq(
        new module(projectName) {
          libraryDependencies ++= Seq(scalaLibrary)
          libraryDependencies.inexactMatch()

          sources := Seq("app")
          testSources := Seq("test")
          resources := Seq("conf", "public")
          excluded := Seq("target")
        },
        new module(s"$projectName-build") {
          sources := Seq("")
          excluded := Seq("project/target", "target")
        }
      )
    }

    runCreateLightbendStarterProjectTest(projectName, templateName, expectedProject)
  }

  private def runCreateLightbendStarterProjectTest(
    projectName: String,
    templateName: String,
    expectedProject: project
  ): Unit = {
    val project: Project = createScalaProject(
      new TechHubProjectTemplate().getName,
      projectName
    ) {
      case projectSettingsStep: ProjectSettingsStep =>
        val settingsStep = projectSettingsStep.getSettingsStepTyped[TechHubModuleBuilder#MySettingsStep]
        settingsStep.setTemplate(templateName)
      case _ =>
    }

    assertProjectsEqual(expectedProject, project)
  }
}
