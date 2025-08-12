package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.SbtProjectCompilationTestBase
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

abstract class SbtProjectWithPureJavaModuleTestBase(incrementality: IncrementalityType, separateModulesForProdTest: Boolean)
  extends SbtProjectCompilationTestBase(separateProdAndTestSources = separateModulesForProdTest) {

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs("project", "module1/src/main/java", "module2/src/main/scala")
    createProjectSubFile("project/build.properties",
      """sbt.version=1.9.6
        |""".stripMargin)
    createProjectSubFile("module1/src/main/java/Greeter.java",
      """interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends Greeter {
        |  def greeting: String = "Hello, world!"
        |}
        |""".stripMargin)
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .aggregate(module1, module2)
        |
        |lazy val module1 = project.in(file("module1"))
        |  .settings(
        |    crossPaths := false,
        |    autoScalaLibrary := false
        |  )
        |
        |lazy val module2 = project.in(file("module2"))
        |  .dependsOn(module1)
        |  .settings(
        |    scalaVersion := "2.13.12"
        |  )
        |""".stripMargin)
  }

  def testImportAndCompile(): Unit = {
    importProject(false)

    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getMyProject).getModules
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = compiler.make()
    val errorsAndWarnings = messages.asScala.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }

    assertTrue(
      s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}",
      errorsAndWarnings.isEmpty
    )

    if (separateModulesForProdTest) {
      findClassFilesAssertions_separateModulesForProdTest(modules)
    } else {
      findClassFilesAssertions(modules)
    }
  }

  private def findClassFilesAssertions(modules: Array[Module]): Unit = {
    val module1 = modules.find(_.getName == "root.module1").orNull
    assertNotNull("Could not find module with name 'root.module1'", module1)
    val module2 = modules.find(_.getName == "root.module2").orNull
    assertNotNull("Could not find module with name 'root.module2'", module2)

    val greeter = compiler.findClassFile("Greeter", module1)
    assertNotNull("Could not find compiled class file Greeter", greeter)

    val helloWorldGreeter = compiler.findClassFile("HelloWorldGreeter", module2)
    assertNotNull("Could not find compiled class file HelloWorldGreeter", helloWorldGreeter)

    val helloWorldGreeterModule = compiler.findClassFile("HelloWorldGreeter$", module2)
    assertNotNull("Could not find compiled class file HelloWorldGreeter$", helloWorldGreeterModule)
  }

  private def findClassFilesAssertions_separateModulesForProdTest(modules: Array[Module]): Unit = {
    val module1Main = modules.find(_.getName == "root.module1.main").orNull
    assertNotNull("Could not find module with name 'root.module1.main'", module1Main)
    val module2Main = modules.find(_.getName == "root.module2.main").orNull
    assertNotNull("Could not find module with name 'root.module2.main'", module2Main)

    val greeter = compiler.findClassFile("Greeter", module1Main)
    assertNotNull("Could not find compiled class file Greeter", greeter)

    val helloWorldGreeter = compiler.findClassFile("HelloWorldGreeter", module2Main)
    assertNotNull("Could not find compiled class file HelloWorldGreeter", helloWorldGreeter)

    val helloWorldGreeterModule = compiler.findClassFile("HelloWorldGreeter$", module2Main)
    assertNotNull("Could not find compiled class file HelloWorldGreeter$", helloWorldGreeterModule)
  }
}

@Category(Array(classOf[CompilationTests_IDEA]))
class SbtProjectWithPureJavaModuleTest_IDEA extends SbtProjectWithPureJavaModuleTestBase(IncrementalityType.IDEA, separateModulesForProdTest = false)

@Category(Array(classOf[CompilationTests_Zinc]))
class SbtProjectWithPureJavaModuleTest_Zinc extends SbtProjectWithPureJavaModuleTestBase(IncrementalityType.SBT, separateModulesForProdTest = false)

@Category(Array(classOf[CompilationTests_IDEA]))
class SbtProjectWithPureJavaModuleTest_separateModulesForProdTest_IDEA extends SbtProjectWithPureJavaModuleTestBase(IncrementalityType.IDEA, separateModulesForProdTest = true)

@Category(Array(classOf[CompilationTests_Zinc]))
class SbtProjectWithPureJavaModuleTest_separateModulesForProdTest_Zinc extends SbtProjectWithPureJavaModuleTestBase(IncrementalityType.SBT, separateModulesForProdTest = true)
