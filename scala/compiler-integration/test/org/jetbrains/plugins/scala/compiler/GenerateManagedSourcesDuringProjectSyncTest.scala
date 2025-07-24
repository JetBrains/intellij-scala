package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
@Category(Array(classOf[SlowTests2]))
abstract class GenerateManagedSourcesDuringProjectSyncTestBase(separateProdAndTestSources: Boolean)
  extends SbtProjectCompilationTestBase(separateProdAndTestSources) {

  @Test
  def compilationSucceedsWhenGenerated(): Unit = {
    setUpBuildInfoProject()

    val projectPath = Path.of(getProjectPath)

    def managedSourcePath(moduleName: String): Path =
      projectPath.resolve(Path.of(moduleName, "target", s"scala-3.7.1", "src_managed", "main", "sbt-buildinfo", "BuildInfo.scala"))

    val buildInfoModule1 = managedSourcePath("module1")
    val virtualFileBuildInfoModule1 = VfsUtil.findFile(buildInfoModule1, true)
    assertNotNull("Managed source file BuildInfo.scala in module1 was not generated during project sync", virtualFileBuildInfoModule1)

    val buildInfoModule3 = managedSourcePath("module3")
    val virtualFileBuildInfoModule3 = VfsUtil.findFile(buildInfoModule3, true)
    assertNotNull("Managed source file BuildInfo.scala in module3 was not generated during project sync", virtualFileBuildInfoModule3)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)
  }

  private def setUpBuildInfoProject(): Unit = {
    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala", "module3/src/main/scala/world")
    createProjectSubFile("project/build.properties", s"sbt.version=1.11.2")
    createProjectSubFile("project/plugins.sbt", s"""addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")""")
    createProjectSubFile("module3/src/main/scala/world/Main.scala",
      """package world
        |
        |object Main:
        |  def main(args: Array[String]): Unit =
        |    println(module1.BuildInfo.name)
        |    println(module1.BuildInfo.version)
        |    println(module3.BuildInfo.scalaVersion)
        |    println(module3.BuildInfo.sbtVersion)
        |""".stripMargin)
    createProjectConfig(
      s"""ThisBuild / scalaVersion := "3.7.1"
         |
         |lazy val root = project.in(file("."))
         |  .aggregate(module1, module2, module3)
         |  .settings(
         |    name := "generateManagedSourcesDuringProjectSyncTest"
         |  )
         |
         |lazy val module1 = project.in(file("module1"))
         |  .enablePlugins(BuildInfoPlugin)
         |  .settings(
         |    name := "module1",
         |    buildInfoKeys := Seq[BuildInfoKey](name, version),
         |    buildInfoPackage := "module1"
         |  )
         |
         |lazy val module2 = project.in(file("module2"))
         |  .dependsOn(module1)
         |
         |lazy val module3 = project.in(file("module3"))
         |  .dependsOn(module2)
         |  .enablePlugins(BuildInfoPlugin)
         |  .settings(
         |    buildInfoKeys := Seq[BuildInfoKey](scalaVersion, sbtVersion),
         |    buildInfoPackage := "module3"
         |  )
         |""".stripMargin)

    importProject(false)

    val modules = ModuleManager.getInstance(getProject).getModules
    val moduleName =
      if (separateProdAndTestSources) "generateManagedSourcesDuringProjectSyncTest.main"
      else "generateManagedSourcesDuringProjectSyncTest"
    rootModule = modules.find(_.getName == moduleName).orNull
    assertNotNull(s"Could not find module with name '$moduleName'", rootModule)
    compiler = new CompilerTester(getProject, java.util.Arrays.asList(modules: _*), null, false)
  }
}

class GenerateManagedSourcesDuringProjectSyncTest
  extends GenerateManagedSourcesDuringProjectSyncTestBase(separateProdAndTestSources = false)

class GenerateManagedSourcesDuringProjectSyncTest_SeparateMainTestModules
  extends GenerateManagedSourcesDuringProjectSyncTestBase(separateProdAndTestSources = true)
