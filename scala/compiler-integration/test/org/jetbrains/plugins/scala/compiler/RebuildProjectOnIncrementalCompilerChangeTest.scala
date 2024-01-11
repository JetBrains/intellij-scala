package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests]))
class RebuildProjectOnIncrementalCompilerChangeTest extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  override lazy val getCurrentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.jdk = sdk.getName
    settings
  }

  override def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override def setUp(): Unit = {
    super.setUp()

    sdk = {
      val res = SmartJDKLoader.getOrCreateJDK()
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala", "module3/src/main/scala")
    createProjectSubFile("project/build.properties",
      """sbt.version=1.9.8
        |""".stripMargin)
    createProjectSubFile("module1/src/main/scala/Greeter.scala",
      """trait Greeter {
        |  def greeting: String
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/GoodMorningGreeter.scala",
      """object GoodMorningGreeter extends Greeter {
        |  override def greeting: String = "Good morning!"
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends Greeter {
        |  override def greeting: String = "Hello, world!"
        |}
        |""".stripMargin)
    createProjectSubFile("module3/src/main/scala/Main.scala",
      """object Main {
        |  def main(args: Array[String]): Unit = {
        |    println(GoodMorningGreeter.greeting)
        |    println(HelloWorldGreeter.greeting)
        |  }
        |}
        |""".stripMargin)
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .aggregate(module1, module2)
        |
        |lazy val module1 = project.in(file("module1"))
        |  .settings(scalaVersion := "3.3.1")
        |
        |lazy val module2 = project.in(file("module2"))
        |  .dependsOn(module1)
        |  .settings(scalaVersion := "3.3.1")
        |
        |lazy val module3 = project.in(file("module3"))
        |  .dependsOn(module2)
        |  .settings(scalaVersion := "3.3.1")
        |""".stripMargin)

    importProject(false)
  }

  override def tearDown(): Unit = try {
    CompileServerLauncher.stopServerAndWait()
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
  } finally {
    super.tearDown()
  }

  def testChangeIncrementalCompilerFromIdeaToSbt(): Unit = {
    testChangeIncrementalCompiler(IncrementalityType.IDEA, IncrementalityType.SBT)
  }

  def testChangeIncrementalCompilerFromSbtToIdea(): Unit = {
    testChangeIncrementalCompiler(IncrementalityType.SBT, IncrementalityType.IDEA)
  }

  private def testChangeIncrementalCompiler(first: IncrementalityType, second: IncrementalityType): Unit = {
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = first

    val modules = ModuleManager.getInstance(myProject).getModules
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages1 = compiler.make()
    val errorsAndWarnings1 = collectErrorsAndWarnings(messages1.asScala.toSeq)

    assertTrue(
      s"Expected no compilation errors or warnings, got: ${errorsAndWarnings1.mkString(System.lineSeparator())}",
      errorsAndWarnings1.isEmpty
    )

    val module1 = findModule("module1", modules)
    val module2 = findModule("module2", modules)
    val module3 = findModule("module3", modules)

    val firstClassFiles = List(
      findClassFile("Greeter", module1),
      findClassFile("GoodMorningGreeter$", module2),
      findClassFile("HelloWorldGreeter$", module2),
      findClassFile("Main$", module3)
    )
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val storagePath = BuildManager.getInstance().getProjectSystemDirectory(myProject).toPath.resolve("incrementalType.dat")
    val incrementality1 = IncrementalityType.valueOf(Files.readString(storagePath, StandardCharsets.UTF_8))
    assertEquals(first, incrementality1)

    compiler.tearDown()
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = second
    saveSettings()

    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages2 = compiler.make()
    val errorsAndWarnings2 = collectErrorsAndWarnings(messages2.asScala.toSeq)

    assertTrue(
      s"Expected no compilation errors or warnings, got: ${errorsAndWarnings2.mkString(System.lineSeparator())}",
      errorsAndWarnings2.isEmpty
    )

    val secondClassFiles = List(
      findClassFile("Greeter", module1),
      findClassFile("GoodMorningGreeter$", module2),
      findClassFile("HelloWorldGreeter$", module2),
      findClassFile("Main$", module3)
    )
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val correct = firstTimestamps.zip(secondTimestamps).forall {
      case (x, y) => x < y
    }
    assertTrue(correct)

    val incrementality2 = IncrementalityType.valueOf(Files.readString(storagePath, StandardCharsets.UTF_8))
    assertEquals(second, incrementality2)
  }

  private def collectErrorsAndWarnings(messages: Seq[CompilerMessage]): Seq[CompilerMessage] =
    messages.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }

  private def findModule(name: String, modules: Array[Module]): Module = {
    val module = modules.find(_.getName == name).orNull
    assertNotNull(s"Could not find module with name '$name'", module)
    module
  }

  private def findClassFile(name: String, module: Module): Path = {
    val file = compiler.findClassFile(name, module)
    assertNotNull(s"Could not find compiled class file '$name'", file)
    file.toPath
  }

  private def saveSettings(): Unit = {
    ScalaCompilerConfiguration.incModificationCount()
    val app = ApplicationManagerEx.getApplicationEx
    try {
      app.setSaveAllowed(true)
      myProject.save()
      app.saveSettings()
    } finally {
      app.setSaveAllowed(false)
    }
  }
}
