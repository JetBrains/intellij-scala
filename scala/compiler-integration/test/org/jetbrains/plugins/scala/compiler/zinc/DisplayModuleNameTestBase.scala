package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.junit.Assert.fail

abstract class DisplayModuleNameTestBase(separateProdAndTestSources: Boolean = false) extends ZincTestBase(separateProdAndTestSources) {

  protected def runTest(expectedValue: Boolean): Unit = {
    importProject(false)
    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
    compiler.rebuild()
    val buildProcessParameters = CompileServerLauncher.buildProcessParameters
    checkUseModuleDisplayName(expectedValue, buildProcessParameters)
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
        |lazy val root = project.in(file("."))
        |lazy val module3 = project.in(file("module3"))
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

  protected def checkUseModuleDisplayName(expectedValue: Boolean, parameters: Seq[String]): Unit = {
    val pattern = """-Duse\.module\.display\.name=(.+)""".r
    val value = parameters.collectFirst { case pattern(matched) => matched }
    value match {
      case Some(x) =>
        val stripped = x.strip()
        if (stripped != expectedValue.toString) {
          fail(s"The value for \"-Duse.module.display.name=\" should be $expectedValue but was $stripped")
        }
      case None => fail("\"-Duse.module.display.name=\" hasn't been found in CompileServerLauncher.buildProcessParameters")
    }
  }
}
