package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
abstract class SbtProjectWithPureJavaModuleTestBase(incrementality: IncrementalityType) extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  override lazy val getCurrentExternalProjectSettings: SbtProjectSettings = {
    val jdkVersion =
      Option(System.getProperty("filter.test.jdk.version"))
        .map(TestJdkVersion.valueOf)
        .getOrElse(TestJdkVersion.JDK_17)
        .toProductionVersion

    val settings = new SbtProjectSettings()
    sdk = SmartJDKLoader.getOrCreateJDK(jdkVersion)
    settings.jdk = sdk.getName
    settings
  }

  override def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def getExternalSystemConfigFileName: String = Sbt.BuildFile

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

  override def tearDown(): Unit = try {
    CompileServerLauncher.ensureServerNotRunning()
    compiler.tearDown()
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
  } finally {
    super.tearDown()
  }

  def testImportAndCompile(): Unit = {
    importProject(false)

    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(myProject).getModules
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = compiler.make()
    val errorsAndWarnings = messages.asScala.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }

    assertTrue(errorsAndWarnings.isEmpty)

    val module1 = modules.find(_.getName == "module1").orNull
    assertNotNull(module1)
    val module2 = modules.find(_.getName == "module2").orNull
    assertNotNull(module2)

    val greeter = compiler.findClassFile("Greeter", module1)
    assertNotNull(greeter)

    val helloWorldGreeter = compiler.findClassFile("HelloWorldGreeter", module2)
    assertNotNull(helloWorldGreeter)

    val helloWorldGreeterModule = compiler.findClassFile("HelloWorldGreeter$", module2)
    assertNotNull(helloWorldGreeterModule)
  }
}

class SbtProjectWithPureJavaModuleTest_IDEA extends SbtProjectWithPureJavaModuleTestBase(IncrementalityType.IDEA)

class SbtProjectWithPureJavaModuleTest_Zinc extends SbtProjectWithPureJavaModuleTestBase(IncrementalityType.SBT)
