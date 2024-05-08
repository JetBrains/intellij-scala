package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.Assert.{assertNotNull, assertNull}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class SharedSourcesUnmanagedDirectoriesTest extends ZincTestBase {

  private var module1: Module = _

  private var module2: Module = _

  private var module3: Module = _

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala", "module3/src/main/scala", "shared/src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("module1/src/main/scala/Foo.scala", "class Foo")
    createProjectSubFile("module2/src/main/scala/Bar.scala", "class Bar extends Foo")
    createProjectSubFile("module3/src/main/scala/Dummy.scala", "class Dummy")
    createProjectSubFile("shared/src/main/scala/Shared.scala", "class Shared")
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .aggregate(module1, module2)
        |
        |lazy val module1 = project.in(file("module1"))
        |  .settings(scalaVersion := "2.13.12", Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala")
        |
        |lazy val module2 = project.in(file("module2"))
        |  .dependsOn(module1)
        |  .settings(scalaVersion := "2.13.12", Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "shared" / "src" / "main" / "scala")
        |
        |lazy val module3 = project.in(file("module3"))
        |  .dependsOn(module1)
        |  .settings(scalaVersion := "2.13.12")
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT

    val modules = ModuleManager.getInstance(myProject).getModules
    module1 = modules.find(_.getName == "root.module1").orNull
    assertNotNull("Could not find module with name 'root.module1'", module1)
    module2 = modules.find(_.getName == "root.module2").orNull
    assertNotNull("Could not find module with name 'root.module2'", module2)
    module3 = modules.find(_.getName == "root.module3").orNull
    assertNotNull("Could not find module with name 'root.module3'", module3)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  def testSharedSourcesOnlyCompiledToOwnerModules(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)

    val module1SharedClass = compiler.findClassFile("Shared", module1)
    val module2SharedClass = compiler.findClassFile("Shared", module2)
    val module3SharedClass = compiler.findClassFile("Shared", module3)

    assertNotNull("Shared class file not found in module1", module1SharedClass)
    assertNotNull("Shared class file not found in module2", module2SharedClass)
    assertNull("Shared class file found in module3, but it shouldn't", module3SharedClass)
  }
}
