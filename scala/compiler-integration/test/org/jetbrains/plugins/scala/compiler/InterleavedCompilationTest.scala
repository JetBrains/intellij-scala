package org.jetbrains.plugins.scala.compiler

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase
import com.intellij.testFramework.{CompilerTester, VfsTestUtil}
import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.{assertCompilingScalaSources, assertNoErrorsOrWarnings}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.SbtCachesSetupUtil
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.nio.file.Path
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests_Zinc]))
@RunWith(classOf[Parameterized])
class InterleavedCompilationTest(jdkVersion: TestJdkVersion) extends SbtProjectCompilationTestBase {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  override def setUp(): Unit = {
    super.setUp()

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
    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = IncrementalityType.SBT

    val modules = ModuleManager.getInstance(getMyProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)
  }

  @Test
  def weirdTrick(): Unit = {
    runSbtCommand("clean")

    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 2)

    runSbtCommand("compile")

    val projectPath = Path.of(getProjectPath)

    val fooSourcePath = projectPath.resolve(Path.of("src", "main", "scala", "Foo.scala"))
    val fooSource = VfsUtil.findFile(fooSourcePath, true)
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
    val srcMainScalaDirVirtualFile = VfsUtil.findFile(srcMainScalaDir, true)
    VfsTestUtil.createFile(srcMainScalaDirVirtualFile, "Client.scala", "class Client { val v: Int = Bar.bar }")

    val messages3 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages3)
    assertCompilingScalaSources(messages3, 1)
  }

  @Test
  def updateTrick(): Unit = {
    val projectPath = Path.of(getProjectPath)

    val srcMainScalaPath = Path.of("src", "main", "scala")

    val fooSourcePath = projectPath.resolve(srcMainScalaPath.resolve("Foo.scala"))
    val fooSource = VfsUtil.findFile(fooSourcePath, true)
    val barSourcePath = projectPath.resolve(srcMainScalaPath.resolve("Bar.scala"))
    val barSource = VfsUtil.findFile(barSourcePath, true)

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
    val launcher = SbtUtil.defaultLauncherPath

    val javaParams = new JavaParameters()
    javaParams.setJarPath(launcher.toCanonicalPath.toString)
    javaParams.setWorkingDirectory(getProjectPath)
    javaParams.setJdk(sdk)
    // Point the forked sbt at the shared TC caches and the JetBrains Maven Central mirror,
    // to avoid HTTP Error 429 Too Many Requests in the CI (the raw fork bypasses SbtSettings.sbtOptions).
    (SbtCachesSetupUtil.cacheAndRepositoryVmOptionsWithBuildReposOverride ++ Seq(
      "-Dsbt.log.noformat=true",
      "-Dfile.encoding=UTF-8",
      "-Djline.terminal=jline.UnsupportedTerminal"
    )).foreach(javaParams.getVMParametersList.add)

    val commandLine = javaParams.toCommandLine
    commandLine.addParameter(command)

    assertEquals(s"sbt $command did not finished with an error", 0, commandLine.createProcess().waitFor())
  }
}

private object InterleavedCompilationTest extends JdkVersionParameters
