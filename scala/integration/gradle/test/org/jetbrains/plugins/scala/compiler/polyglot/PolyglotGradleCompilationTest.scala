package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.{CompilerTester, IndexingTestUtil}
import junit.framework.TestCase.{assertEquals, assertNotNull}
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.{CompileServerTestUtil, JdkVersionDiscovery}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.gradle.GradleTestUtil
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.junit.experimental.categories.Category

@Category(Array(classOf[CompilationTests_Zinc]))
class PolyglotGradleCompilationTest extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  private var module1: Module = _

  private var module2: Module = _

  override lazy val getCurrentExternalProjectSettings: GradleProjectSettings = {
    val settings = new GradleProjectSettings().withQualifiedModuleNames()
    settings.setGradleJvm(sdk.getName)
    settings.setDelegatedBuild(false)
    settings
  }

  override def getExternalSystemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def getExternalSystemConfigFileName: String = GradleConstants.DEFAULT_SCRIPT_NAME

  override def setUp(): Unit = {
    super.setUp()

    GradleTestUtil.setupGradleHome(getProject)

    sdk = {
      val jdkVersion = JdkVersionDiscovery.discoveredJdk
      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    CompileServerTestUtil.registerLongRunningThreads()

    createProjectSubDirs("module1/src/main/java", "module1/src/main/kotlin", "module2/src/main/scala")
    createProjectSubFile("settings.gradle",
      """rootProject.name = 'polyglot-gradle'
        |include 'module1', 'module2'
        |""".stripMargin)
    createProjectSubFile("module1/build.gradle",
      """plugins {
        |  id 'java'
        |  id 'org.jetbrains.kotlin.jvm' version '2.0.21'
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
        |repositories {
        |  mavenCentral()
        |}
        |""".stripMargin)
    createProjectSubFile("module2/build.gradle",
      """plugins {
        |  id 'scala'
        |}
        |
        |group = 'org.example'
        |version = '1.0-SNAPSHOT'
        |
        |repositories {
        |  mavenCentral()
        |}
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

    KotlinDaemonUtil.disableKotlinDaemon(getProject)

    val modules = ModuleManager.getInstance(getProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    IndexingTestUtil.waitUntilIndexesAreReady(getProject)

    module1 = modules.find(_.getName == "polyglot-gradle.module1.main").orNull
    assertNotNull("Could not find module with name 'polyglot-gradle.module1.main'", module1)
    module2 = modules.find(_.getName == "polyglot-gradle.module2.main").orNull
    assertNotNull("Could not find module with name 'polyglot-gradle.module2.main'", module2)
    compiler = new CompilerTester(getProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.removeJdk(sdk)
      val kotlinSdk = jdkTable.getAllJdks.find(_.getName.contains("Kotlin SDK"))
      kotlinSdk.foreach(jdkTable.removeJdk)
    }
  } finally {
    super.tearDown()
  }

  def testPolyglotCompilation(): Unit = {
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType)
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
