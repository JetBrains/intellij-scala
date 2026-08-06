package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.Assert.assertEquals

abstract class DisplayModuleNameTestBase(jdkVersion: TestJdkVersion, separateProdAndTestSources: Boolean = false)
  extends SbtProjectCompilationTestBase(separateProdAndTestSources) {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  protected def runTest(expectedValue: Boolean): Unit = {
    importProject(false)
    val project = getMyProject
    val modules = ModuleManager.getInstance(project).getModules
    rootModule = modules.find(_.getName == "root").orNull
    compiler = new CompilerTester(project, java.util.Arrays.asList(modules: _*), null, false)
    compiler.rebuild()
    assertUseModuleDisplayName(expectedValue, project)
  }

  protected def createSingleBuildProject(): Unit = {
    createProjectSubDirs("project", "module1", "module2")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectConfig(
      """ThisBuild / scalaVersion := "2.13.12"
        |lazy val root = project.in(file("."))
        |lazy val module1 = project.in(file("module1"))
        |lazy val module2 = project.in(file("module2"))
        |""".stripMargin)
  }

  protected def createMultipleBuildsProjectWithUniqueNames(): Unit = {
    createProjectSubDirs("project", "module1", "module2", "root1/module3", "root1/project")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("root1/project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("root1/build.sbt",
      """ThisBuild / scalaVersion := "2.13.12"
        |lazy val root1 = project.in(file("."))
        |lazy val module3 = project.in(file("module3"))
        |""".stripMargin)
    createProjectConfig(
      """ThisBuild / scalaVersion := "2.13.12"
        |val root1 = ProjectRef(file("root1"), "root1")
        |lazy val root = project.in(file("."))
        |   .dependsOn(root1)
        |lazy val module1 = project.in(file("module1"))
        |lazy val module2 = project.in(file("module2"))
        |""".stripMargin)
  }

  protected def createMultipleBuildsProjectWithDuplicatedNames(): Unit = {
    createProjectSubDirs("project", "module1", "module2", "root1/module2", "root1/project")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("root1/project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("root1/build.sbt",
      """ThisBuild / scalaVersion := "2.13.12"
        |lazy val root = project.in(file("."))
        |lazy val module2 = project.in(file("module2"))
        |""".stripMargin)
    createProjectConfig(
      """ThisBuild / scalaVersion := "2.13.12"
        |val root1 = ProjectRef(file("root1"), "root")
        |lazy val root = project.in(file("."))
        |   .dependsOn(root1)
        |lazy val module1 = project.in(file("module1"))
        |lazy val module2 = project.in(file("module2"))
        |""".stripMargin)
  }

  private def assertUseModuleDisplayName(expectedValue: Boolean, project: Project): Unit = {
    val useModuleDisplayName = ProjectMetadataUtil.computeUseModuleDisplayName(project)
    assertEquals("Use module display name was not properly configured", expectedValue, useModuleDisplayName)
  }
}
