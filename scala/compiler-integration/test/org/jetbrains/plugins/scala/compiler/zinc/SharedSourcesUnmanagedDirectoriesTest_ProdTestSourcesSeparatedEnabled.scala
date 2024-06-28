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
class SharedSourcesUnmanagedDirectoriesTest_ProdTestSourcesSeparatedEnabled extends ZincTestBase(separateProdAndTestSources = true) {

  private var module1Main: Module = _
  private var module1Test: Module = _

  private var module2Main: Module = _
  private var module2Test: Module = _

  private var module3Main: Module = _
  private var module3Test: Module = _

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala", "module3/src/main/scala", "shared/src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("module1/src/main/scala/Foo.scala", "class Foo")
    createProjectSubFile("module1/src/test/scala/FooTest.scala", "class FooTest")
    createProjectSubFile("module2/src/main/scala/Bar.scala", "class Bar extends Foo")
    createProjectSubFile("module2/src/test/scala/BarTest.scala", "class BarTest")
    createProjectSubFile("module3/src/main/scala/Dummy.scala", "class Dummy")
    createProjectSubFile("module3/src/test/scala/DummyTest.scala", "class DummyTest")
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
    module1Main = modules.find(_.getName == "root.module1.main").orNull
    assertNotNull("Could not find module with name 'root.module1.main'", module1Main)
    module1Test = modules.find(_.getName == "root.module1.test").orNull
    assertNotNull("Could not find module with name 'root.module1.test'", module1Test)
    module2Main = modules.find(_.getName == "root.module2.main").orNull
    assertNotNull("Could not find module with name 'root.module2.main'", module2Main)
    module2Test = modules.find(_.getName == "root.module2.test").orNull
    assertNotNull("Could not find module with name 'root.module2.test'", module2Test)
    module3Main = modules.find(_.getName == "root.module3.main").orNull
    assertNotNull("Could not find module with name 'root.module3.main'", module3Main)
    module3Test = modules.find(_.getName == "root.module3.test").orNull
    assertNotNull("Could not find module with name 'root.module3.test'", module3Test)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  def testSharedSourcesOnlyCompiledToOwnerModules(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)

    Seq(module1Main, module2Main).foreach { module =>
      val sharedClass = compiler.findClassFile("Shared", module)
      assertNotNull(s"Shared class file not found in ${module.getName}", sharedClass)
    }

    val sharedClass = compiler.findClassFile("Shared", module3Main)
    assertNull(s"Shared class file found in ${module3Main.getName}, but it shouldn't", sharedClass)

    Seq(module1Test, module2Test, module3Test).foreach { module =>
      val sharedClass = findClassFile("Shared", module, isTest = true)
      assertNull(s"Shared class file found in ${module.getName}, but it shouldn't", sharedClass)
    }
  }
}
