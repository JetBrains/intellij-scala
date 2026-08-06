package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{ActionManager, ActionUiKind, AnActionEvent}
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.testFramework.CompilerTester
import junit.framework.TestCase.{assertEquals, assertNotNull, assertTrue}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.compiletime.uninitialized
import scala.concurrent.TimeoutException
import scala.jdk.CollectionConverters.*

@Category(Array(classOf[SlowTests]))
class SbtGenerateManagedSourcesActionTest extends SbtProjectCompilationTestBase {

  override protected def jdkVersionForTest: TestJdkVersion = TestJdkVersion.JDK_17

  private var module1: Module = uninitialized
  private var module2: Module = uninitialized
  private var module3: Module = uninitialized

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs("project", "module1/src/main/scala", "module2/src/main/scala", "module3/src/main/scala")
    createProjectSubFile("project/build.properties",
      """sbt.version=1.10.7
        |""".stripMargin)
    createProjectSubFile("project/plugins.sbt",
      """addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
        |""".stripMargin)
    createProjectSubFile("module1/src/main/scala/Example1.scala",
      """object Example1:
         |  val scalaVersion: String = com.example.module1.BuildInfo.scalaVersion
         |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/Example2.scala",
      """object Example2:
        |  val scalaVersion: String = com.example.module2.BuildInfo.scalaVersion
        |""".stripMargin)
    createProjectSubFile("module3/src/main/scala/Example3.scala",
      """object Example3:
        |  val scalaVersion: String = com.example.module3.BuildInfo.scalaVersion
        |""".stripMargin)
    createProjectConfig(
      """ThisBuild / scalaVersion := "3.6.2"
        |
        |lazy val root = project.in(file("."))
        |  .settings(
        |    name := "generate-managed-sources"
        |  )
        |
        |lazy val module1 = project.in(file("module1"))
        |  .enablePlugins(BuildInfoPlugin)
        |  .settings(
        |    buildInfoKeys := Seq(scalaVersion),
        |    buildInfoPackage := "com.example.module1"
        |  )
        |
        |lazy val module2 = project.in(file("module2"))
        |  .enablePlugins(BuildInfoPlugin)
        |  .settings(
        |    buildInfoKeys := Seq(scalaVersion),
        |    buildInfoPackage := "com.example.module2"
        |  )
        |
        |lazy val module3 = project.in(file("module3"))
        |  .enablePlugins(BuildInfoPlugin)
        |  .settings(
        |    buildInfoKeys := Seq(scalaVersion),
        |    buildInfoPackage := "com.example.module3"
        |  )
        |""".stripMargin
    )

    importProject(false)

    val modules = ModuleManager.getInstance(getMyProject).getModules
    rootModule = findModule("generate-managed-sources", modules)
    module1 = findModule("generate-managed-sources.module1", modules)
    module2 = findModule("generate-managed-sources.module2", modules)
    module3 = findModule("generate-managed-sources.module3", modules)
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)
  }

  def testGenerateManagedSources(): Unit = {
    val basePath = Path.of(getProjectPath)
    val buildInfoGeneratedSourcePaths = Seq(
      buildInfoPath("module1"),
      buildInfoPath("module2"),
      buildInfoPath("module3")
    ).map(basePath.resolve)

    buildInfoGeneratedSourcePaths.foreach { path =>
      val deleted = Files.deleteIfExists(path)
      assertTrue(s"Could not delete generated source file $path (generated during project import)", deleted)
    }

    val messages1 = compiler.make().asScala.toSeq
    val errors1 = messages1.collect {
      case m if m.getCategory == CompilerMessageCategory.ERROR => m
    }
    assertEquals(3, errors1.size)

    val action = ActionManager.getInstance().getAction("Scala.Sbt.GenerateManagedSources")
    assertNotNull("Could not find registered action with id 'Scala.Sbt.GenerateManagedSources'", action)

    val actionEvent = createDummyActionEvent
    action.actionPerformed(actionEvent)

    var allGenerated = false
    var retries = 100
    while (!allGenerated && retries >= 0) {
      Thread.sleep(300L)
      retries -= 1
      allGenerated = buildInfoGeneratedSourcePaths.forall(Files.exists(_))
    }

    if (!allGenerated) throw new TimeoutException("Generating managed sources took too long")

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
  }

  private def findModule(name: String, modules: Array[Module]): Module = {
    val m = modules.find(_.getName == name).orNull
    assertNotNull(s"Could not find module with name $name", m)
    m
  }

  private def buildInfoPath(module: String): Path =
    Path.of(module, "target", "scala-3.6.2", "src_managed", "main", "sbt-buildinfo", "BuildInfo.scala")

  private def createDummyActionEvent: AnActionEvent = {
    val context = SimpleDataContext.getProjectContext(this.getMyProject)
    AnActionEvent.createEvent(context, null, "fake-place-for-tests", ActionUiKind.NONE, null)
  }
}
