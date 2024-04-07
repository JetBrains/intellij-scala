package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase
import com.intellij.testFramework.{CompilerTester, VfsTestUtil}
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.SbtUtil
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.experimental.categories.Category

import java.nio.file.Path
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class InterleavedCompilationTest extends ZincTestBase {

  override lazy val getCurrentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.jdk = sdk.getName
    settings
  }

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def setUp(): Unit = {
    super.setUp()

    sdk = {
      val jdkVersion =
        Option(System.getProperty("filter.test.jdk.version"))
          .map(TestJdkVersion.valueOf)
          .getOrElse(TestJdkVersion.JDK_17)
          .toProductionVersion

      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    createProjectSubDirs("project", "src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("src/main/scala/Foo.scala", "object Foo { def foo = 5 }")
    createProjectSubFile("src/main/scala/Bar.scala", "object Bar { def bar = Foo.foo }")
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .settings(
        |    scalaVersion := "2.13.12"
        |  )
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT

    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
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

  def testWeirdTrick(): Unit = {
    runSbtCommand("clean")

    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 2)

    runSbtCommand("compile")

    val projectPath = Path.of(getProjectPath)

    val fooSourcePath = projectPath.resolve(Path.of("src", "main", "scala", "Foo.scala"))
    val fooSource = VfsUtil.findFileByIoFile(fooSourcePath.toFile, true)
    inWriteAction {
      VfsUtil.saveText(fooSource, """object Foo { def foo = "123" }""")
    }

    runSbtCommand("compile")

    inWriteAction {
      VfsUtil.saveText(fooSource, """object Foo { def foo = 123 }""")
    }

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 2)

    val srcMainScalaDir = projectPath.resolve(Path.of("src", "main", "scala"))
    val srcMainScalaDirVirtualFile = VfsUtil.findFileByIoFile(srcMainScalaDir.toFile, true)
    VfsTestUtil.createFile(srcMainScalaDirVirtualFile, "Client.scala", "class Client { val v: Int = Bar.bar }")

    val messages3 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages3)
    assertCompilingScalaSources(messages3, 1)
  }

  def testUpdateTrick(): Unit = {
    val projectPath = Path.of(getProjectPath)

    val srcMainScalaPath = Path.of("src", "main", "scala")

    val fooSourcePath = projectPath.resolve(srcMainScalaPath.resolve("Foo.scala"))
    val fooSource = VfsUtil.findFileByIoFile(fooSourcePath.toFile, true)
    val barSourcePath = projectPath.resolve(srcMainScalaPath.resolve("Bar.scala"))
    val barSource = VfsUtil.findFileByIoFile(barSourcePath.toFile, true)

    inWriteAction {
      VfsUtil.saveText(barSource, "object Bar")
    }

    runSbtCommand("clean")

    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 2)

    runSbtCommand("compile")

    ExternalSystemTestCase.setFileContent(fooSource, "object Foo", /* advanceStamps = */ false)

    runSbtCommand("compile")

    ExternalSystemTestCase.setFileContent(fooSource, "object Foo { def foo = 5 }", /* advanceStamps = */ false)

    inWriteAction {
      VfsUtil.saveText(barSource, "object Bar { Foo.foo }")
    }

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 2)
  }

  private def runSbtCommand(command: String): Unit = {
    val launcher = SbtUtil.getDefaultLauncher

    val javaParams = new JavaParameters()
    javaParams.setJarPath(launcher.getCanonicalPath)
    javaParams.setWorkingDirectory(getProjectPath)
    javaParams.setJdk(sdk)

    val commandLine = javaParams.toCommandLine
    commandLine.addParameter(command)

    assertEquals(s"sbt $command did not finished with an error", 0, commandLine.createProcess().waitFor())
  }

}
