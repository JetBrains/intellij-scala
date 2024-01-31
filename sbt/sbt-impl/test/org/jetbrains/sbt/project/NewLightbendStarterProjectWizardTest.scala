package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.DependencyManagerBase.scalaLibraryDescription
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
    // The smallest Lightbend template with few dependencies
    val templateName = "Hello, Scala!"
    val scalaVersion = ScalaVersion.fromString("2.13.12").get

    //noinspection TypeAnnotation
    val expectedProject = new project(projectName) {
      // NOTE: we do not test path of scala-library & SDK classpath for "Hello, Scala!"
      // template because it uses paths to `.sbt/boot` artifacts: `~/.sbt/boot/scala-2.12.10/lib/scala-reflect.jar`
      // (because scala version in the template is the same as used by sbt itself)
      lazy val scalaLibrary = new library(s"sbt: ${scalaLibraryDescription(scalaVersion)}:jar") {
        scalaSdkSettings := Some(ScalaSdkAttributes(
          scalaVersion.languageLevel,
          classpath = None
        ))
      }

      lazy val scalaTestLibraries = Seq(
        "sbt: junit:junit:4.13.2:jar",
        "sbt: org.hamcrest:hamcrest-core:1.3:jar",
        "sbt: org.scala-sbt:test-interface:1.0:jar",
        "sbt: org.scalameta:junit-interface:0.7.29:jar",
        "sbt: org.scalameta:munit_2.13:0.7.29:jar"
      ).map(new library(_))

      // NOTE: actually there are much more libraries in the dependencies but we health-check just a single one
      lazy val myLibraries = scalaLibrary +: scalaTestLibraries

      libraries ++= myLibraries
      modules := Seq(
        new module(projectName) {
          libraryDependencies ++= myLibraries

          sources := Seq("src/main/scala")
          testSources := Seq("src/test/scala")
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
        val settingsStep = projectSettingsStep.getSettingsStepTyped[TechHubModuleBuilder#Step]
        settingsStep.setTemplate(templateName)
      case _ =>
    }

    assertProjectsEqual(expectedProject, project)
  }
}
