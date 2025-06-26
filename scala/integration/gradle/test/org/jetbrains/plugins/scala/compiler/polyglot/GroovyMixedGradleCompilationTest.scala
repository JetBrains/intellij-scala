package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.{Module, ModuleManager, ModuleTypeManager, StdModuleTypes}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.{CompilerTester, IndexingTestUtil}
import junit.framework.TestCase.{assertEquals, assertNotNull}
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, JdkVersionDiscovery}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.gradle.GradleTestUtil
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class GroovyMixedGradleCompilationTest extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  private var mainModule: Module = _

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

    createProjectSubDirs("src/main/groovy", "src/main/java", "src/main/kotlin", "src/main/scala")
    createProjectSubFile("settings.gradle",
      """rootProject.name = 'groovy-mixed'
        |""".stripMargin)
    createProjectConfig(
      """plugins {
        |    id 'groovy'
        |    id 'java'
        |    id 'org.jetbrains.kotlin.jvm' version '2.1.0'
        |    id 'scala'
        |}
        |
        |group = 'org.example'
        |version = '1.0-SNAPSHOT'
        |
        |repositories {
        |    mavenCentral()
        |}
        |
        |dependencies {
        |    implementation 'org.apache.groovy:groovy:4.0.14'
        |    implementation 'org.scala-lang:scala3-library_3:3.5.2'
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/groovy/Greeter.groovy",
      """interface Greeter {
        |    String greeting()
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/groovy/GroovyGreeter.groovy",
      """class GroovyGreeter implements Greeter {
        |    @Override
        |    String greeting() {
        |        return "Hello from Groovy!"
        |    }
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/java/JavaGreeter.java",
      """public class JavaGreeter implements Greeter {
        |    @Override
        |    public String greeting() {
        |        return "Hello from Java!";
        |    }
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/kotlin/KotlinGreeter.kt",
      """class KotlinGreeter : Greeter {
        |    override fun greeting(): String = "Hello from Kotlin!"
        |}
        |""".stripMargin)
    createProjectSubFile("src/main/scala/ScalaGreeter.scala",
      """class ScalaGreeter extends Greeter:
        |  override def greeting(): String = "Hello from Scala!"
        |""".stripMargin)
    createProjectSubFile("src/main/scala/main.scala",
      """@main
        |def main(): Unit =
        |  val g = GroovyGreeter()
        |  val j = JavaGreeter()
        |  val k = KotlinGreeter()
        |  val s = ScalaGreeter()
        |  println(g.greeting())
        |  println(j.greeting())
        |  println(k.greeting())
        |  println(s.greeting())
        |""".stripMargin)

    importProject()

    KotlinDaemonUtil.disableKotlinDaemon(getProject)

    val modules = ModuleManager.getInstance(getProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    IndexingTestUtil.waitUntilIndexesAreReady(getProject)

    mainModule = modules.find(_.getName == "groovy-mixed.main").orNull
    assertNotNull("Could not find module with name 'groovy-mixed.main'", mainModule)
    compiler = new CompilerTester(getProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  override def tearDown(): Unit = try {
    CompileServerLauncher.stopServerAndWait()
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
    //noinspection ApiStatus
    ModuleTypeManager.getInstance.unregisterModuleType(StdModuleTypes.JAVA)
  } finally {
    super.tearDown()
  }

  def testMixedGroovyCompilation(): Unit = {
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType)
    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)
    for (cls <- Seq("Greeter", "GroovyGreeter", "JavaGreeter", "KotlinGreeter", "ScalaGreeter", "main$package", "main$package$")) {
      assertClassExists(cls, mainModule)
    }
  }

  private def assertClassExists(name: String, module: Module): Unit = {
    val file = compiler.findClassFile(name, module)
    assertNotNull(s"Could not find class file for $name", file)
  }
}
