package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.{CompilerTester, IndexingTestUtil}
import junit.framework.TestCase.{assertEquals, assertNotNull}
import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.{JdkVersionParameters, SbtProjectCompilationTestBase}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.compiletime.uninitialized

@Category(Array(classOf[CompilationTests_Zinc]))
@RunWith(classOf[Parameterized])
abstract class PolyglotSbtCompilationTestBase(jdkVersion: TestJdkVersion, separateModules: Boolean)
  extends SbtProjectCompilationTestBase(separateProdAndTestSources = separateModules) {

  override protected def jdkVersionForTest: TestJdkVersion = jdkVersion

  private var module1: Module = uninitialized

  private var module2: Module = uninitialized

  override def setUp(): Unit = {
    super.setUp()

    createProjectSubDirs("project", "module1/src/main/java", "module1/src/main/kotlin", "module2/src/main/scala")
    createProjectSubFile("project/build.properties",
      """sbt.version=1.10.5
        |""".stripMargin)
    createProjectSubFile("project/plugins.sbt",
      """addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % "3.1.4")
        |""".stripMargin)
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .aggregate(module1, module2)
        |  .settings(
        |    name := "polyglot-sbt"
        |  )
        |
        |lazy val module1 = project.in(file("module1"))
        |  .enablePlugins(KotlinPlugin)
        |
        |lazy val module2 = project.in(file("module2"))
        |  .dependsOn(module1)
        |  .settings(
        |    scalaVersion := "2.13.15"
        |  )
        |""".stripMargin)
    createProjectSubFile("module1/src/main/java/Greeter.java",
      """public interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    createProjectSubFile("module1/src/main/kotlin/AbstractGreeter.kt",
      """abstract class AbstractGreeter(private val str: String) : Greeter {
        |  override fun greeting(): String = str
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends AbstractGreeter("Hello, world!")
        |""".stripMargin)

    importProject(false)

    KotlinDaemonUtil.disableKotlinDaemon(getMyProject)

    val modules = ModuleManager.getInstance(getMyProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    IndexingTestUtil.waitUntilIndexesAreReady(getMyProject)

    val module1Name = if (separateModules) "polyglot-sbt.module1.main" else "polyglot-sbt.module1"
    val module2Name = if (separateModules) "polyglot-sbt.module2.main" else "polyglot-sbt.module2"

    module1 = modules.find(_.getName == module1Name).orNull
    assertNotNull(s"Could not find module with name '$module1Name'", module1)
    module2 = modules.find(_.getName == module2Name).orNull
    assertNotNull(s"Could not find module with name '$module2Name'", module2)
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)
  }

  override def tearDown(): Unit = try {
    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      val kotlinSdk = jdkTable.getAllJdks.find(_.getName.contains("Kotlin SDK"))
      kotlinSdk.foreach(jdkTable.removeJdk)
    }
  } finally {
    super.tearDown()
  }

  @Test
  def testPolyglotCompilation(): Unit = {
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType)
    compiler.make()
    assertClassExists("Greeter", module1)
    assertClassExists("AbstractGreeter", module1)
    assertClassExists("HelloWorldGreeter", module2)
  }

  private def assertClassExists(name: String, module: Module): Unit = {
    val file = compiler.findClassFile(name, module)
    assertNotNull(s"Could not find class file for $name", file)
  }
}

class PolyglotSbtCompilationTest(jdkVersion: TestJdkVersion)
  extends PolyglotSbtCompilationTestBase(jdkVersion, separateModules = false)

private object PolyglotSbtCompilationTest extends JdkVersionParameters

class PolyglotSbtCompilationWithSeparateModulesTest(jdkVersion: TestJdkVersion)
  extends PolyglotSbtCompilationTestBase(jdkVersion, separateModules = true)

private object PolyglotSbtCompilationWithSeparateModulesTest extends JdkVersionParameters
