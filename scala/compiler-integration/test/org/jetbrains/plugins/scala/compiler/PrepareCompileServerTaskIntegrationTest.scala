package org.jetbrains.plugins.scala.compiler

import com.intellij.pom.java.LanguageLevel
import com.intellij.task.ProjectTaskManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ExceptionUtil
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.build.BuildDiagnosticsCollector
import org.jetbrains.plugins.scala.compiler.PrepareCompileServerTaskIntegrationTest.ProjectTaskTimeoutMillis
import org.jetbrains.plugins.scala.compiler.testUtils.CompilerUtils
import org.jetbrains.plugins.scala.util.CollectingLoggedMessagesProcessor
import org.junit.Assert.{assertFalse, assertTrue, fail}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Covers automatic compile-server startup through the high-level IDE build task API available in tests.
 *
 * This keeps the test close to production/user-visible entry points such as Compile File, Build Module,
 * Rebuild Module, Build Project, and Rebuild Project.
 * This is achieved by using [[ProjectTaskManager]] APIs
 */
@RunWith(classOf[JUnit4])
class PrepareCompileServerTaskIntegrationTest extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_21

  override protected def setUp(): Unit = {
    super.setUp()

    // Without this call there can be a `scala: No JDK in module ...` exception when invoking
    // com.intellij.task.ProjectTaskManager.buildAllModules (which is done in some tests)
    CompilerUtils.prepareExternalCompilerModel(getProject)
  }

  @Test
  def compileServerShouldAutomaticallyStart_WhenCompilingSingleFile(): Unit = {
    val file = addPersonSource()

    assertCompileServerStartsAfterProjectTask("single-file compilation") {
      _.compile(file)
    }
  }

  @Test
  def compileServerShouldAutomaticallyStart_WhenBuildingModule(): Unit = {
    addPersonSource()

    assertCompileServerStartsAfterProjectTask("module build") {
      _.build(getModule)
    }
  }

  @Test
  def compileServerShouldAutomaticallyStart_WhenRebuildingModule(): Unit = {
    addPersonSource()

    assertCompileServerStartsAfterProjectTask("module rebuild") {
      _.rebuild(getModule)
    }
  }

  @Test
  def compileServerShouldAutomaticallyStart_WhenBuildingProject(): Unit = {
    addPersonSource()

    assertCompileServerStartsAfterProjectTask("project build") {
      _.buildAllModules()
    }
  }

  @Test
  def compileServerShouldAutomaticallyStart_WhenRebuildingProject(): Unit = {
    addPersonSource()

    assertCompileServerStartsAfterProjectTask("project rebuild") {
      _.rebuildAllModules()
    }
  }

  private def addPersonSource() =
    addFileToProjectSources("org/example/Person.scala",
      """package org.example
        |
        |final case class Person(name: String, age: Int)
        |""".stripMargin)

  private def assertCompileServerStartsAfterProjectTask(compilationName: String)(
    runTask: ProjectTaskManager => Promise[ProjectTaskManager.Result]
  ): Unit = {
    CompileServerLauncher.stopServerAndWait()
    assertFalse(s"Compile server is running before $compilationName", CompileServerLauncher.running)

    val ((result, diagnostics), loggedErrors) = CollectingLoggedMessagesProcessor.collectErrorThrowables {
      BuildDiagnosticsCollector.capture(getProject) {
        val promise = runTask(ProjectTaskManager.getInstance(getProject))
        val result = PlatformTestUtil.waitForPromise(promise, ProjectTaskTimeoutMillis)
        if (result == null) {
          fail(s"Project task did not finish after $ProjectTaskTimeoutMillis ms")
        }
        result
      }
    }

    assertTrue(
      s"""Compile server is not running after $compilationName.
         |Logged errors:
         |${loggedErrorsText(loggedErrors)}
         |Build diagnostics:
         |${diagnostics.rendered}
         |Project task result:
         |aborted=${result.isAborted}, hasErrors=${result.hasErrors}
         |""".stripMargin,
      CompileServerLauncher.running
    )
    assertFalse(s"Project task was aborted after $compilationName.${diagnostics.rendered}", result.isAborted)
    assertFalse(s"Project task had errors after $compilationName.${diagnostics.rendered}", result.hasErrors)
  }

  private def loggedErrorsText(errors: Seq[Throwable]): String =
    if (errors.isEmpty) "<no logged errors captured>"
    else errors.map(ExceptionUtil.getThrowableText).mkString(System.lineSeparator())
}

private object PrepareCompileServerTaskIntegrationTest {
  private val ProjectTaskTimeoutMillis = 60000L
}
