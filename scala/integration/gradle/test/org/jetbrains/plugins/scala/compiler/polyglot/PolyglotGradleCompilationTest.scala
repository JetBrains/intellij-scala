package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{CompilerTester, IndexingTestUtil}
import junit.framework.TestCase.{assertEquals, assertNotNull}
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.JdkVersionParameters
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.gradle.GradleTestUtil
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.compiletime.uninitialized

@Category(Array(classOf[CompilationTests_Zinc]))
@RunWith(classOf[Parameterized])
class PolyglotGradleCompilationTest(jdkVersion: TestJdkVersion) extends ExternalSystemImportingTestCase {

  private var gradleSdk: Sdk = uninitialized

  private var sdk: Sdk = uninitialized

  private var compiler: CompilerTester = uninitialized

  private var module1: Module = uninitialized

  private var module2: Module = uninitialized

  override lazy val getCurrentExternalProjectSettings: GradleProjectSettings = {
    val settings = new GradleProjectSettings().withQualifiedModuleNames()
    settings.setGradleJvm(gradleSdk.getName)
    settings.setDelegatedBuild(false)
    settings
  }

  override def getExternalSystemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def getExternalSystemConfigFileName: String = GradleConstants.DEFAULT_SCRIPT_NAME

  override def setUp(): Unit = {
    super.setUp()

    GradleTestUtil.setupGradleHome(getMyProject)

    gradleSdk = SmartJDKLoader.getOrCreateJDK(LanguageLevel.JDK_17)

    sdk = {
      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion.toProductionVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    CompileServerTestUtil.registerLongRunningThreads()

    createProjectSubDirs("module1/src/main/java", "module1/src/main/kotlin", "module2/src/main/scala")
    createProjectSubFile("settings.gradle",
      s"""${GradleTestUtil.pluginManagementBlock}
        |
        |rootProject.name = 'polyglot-gradle'
        |include 'module1', 'module2'
        |""".stripMargin)
    createProjectSubFile("module1/build.gradle",
      s"""plugins {
        |  id 'java'
        |  id 'org.jetbrains.kotlin.jvm' version '2.3.21'
        |}
        |
        |group = 'org.example'
        |version = '1.0-SNAPSHOT'
        |
        |java {
        |  sourceCompatibility = JavaVersion.VERSION_1_8
        |  targetCompatibility = JavaVersion.VERSION_1_8
        |}
        |
        |${GradleTestUtil.repositoriesBlock}
        |""".stripMargin)
    createProjectSubFile("module2/build.gradle",
      s"""plugins {
        |  id 'scala'
        |}
        |
        |group = 'org.example'
        |version = '1.0-SNAPSHOT'
        |
        |${GradleTestUtil.repositoriesBlock}
        |
        |dependencies {
        |  implementation 'org.scala-lang:scala-library:2.13.15'
        |  implementation project(':module1')
        |}
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

    importProject()

    KotlinDaemonUtil.disableKotlinDaemon(getMyProject)

    val modules = ModuleManager.getInstance(getMyProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    IndexingTestUtil.waitUntilIndexesAreReady(getMyProject)

    module1 = modules.find(_.getName == "polyglot-gradle.module1.main").orNull
    assertNotNull("Could not find module with name 'polyglot-gradle.module1.main'", module1)
    module2 = modules.find(_.getName == "polyglot-gradle.module2.main").orNull
    assertNotNull("Could not find module with name 'polyglot-gradle.module2.main'", module2)
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      Seq(sdk, gradleSdk).foreach(jdkTable.removeJdk)
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

private object PolyglotGradleCompilationTest extends JdkVersionParameters
