package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.{JdkVersionParameters, SbtProjectCompilationTestBase}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.jdk.CollectionConverters._

@RunWith(classOf[Parameterized])
abstract class SbtProjectWithPureJavaModuleTestBase(jdkVersion: TestJdkVersion, separateModulesForProdTest: Boolean)
  extends SbtProjectCompilationTestBase(separateProdAndTestSources = separateModulesForProdTest) {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

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

  @Test
  @Category(Array(classOf[CompilationTests_Zinc]))
  def importAndCompile_Zinc(): Unit = {
    runImportAndCompileTest(IncrementalityType.SBT)
  }

  @Test
  @Category(Array(classOf[CompilationTests_IDEA]))
  def importAndCompile_IDEA(): Unit = {
    runImportAndCompileTest(IncrementalityType.IDEA)
  }

  private def runImportAndCompileTest(incrementality: IncrementalityType): Unit = {
    importProject(false)

    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getMyProject).getModules
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)

    val jdk21warnings = Set(
      "scala: source value 8 is obsolete and will be removed in a future release",
      "scala: target value 8 is obsolete and will be removed in a future release",
      "scala: To suppress warnings about obsolete options, use -Xlint:-options"
    )

    val messages = compiler.make()
    val errorsAndWarnings = messages.asScala.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }.filterNot(msg => jdk21warnings.exists(prefix => msg.getMessage.startsWith(prefix)))

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

class SbtProjectWithPureJavaModuleTest(jdkVersion: TestJdkVersion)
  extends SbtProjectWithPureJavaModuleTestBase(jdkVersion, separateModulesForProdTest = false)

private object SbtProjectWithPureJavaModuleTest extends JdkVersionParameters

class SbtProjectWithPureJavaModuleTest_SeparateModulesForProdTest(jdkVersion: TestJdkVersion)
  extends SbtProjectWithPureJavaModuleTestBase(jdkVersion, separateModulesForProdTest = true)

private object SbtProjectWithPureJavaModuleTest_SeparateModulesForProdTest extends JdkVersionParameters
