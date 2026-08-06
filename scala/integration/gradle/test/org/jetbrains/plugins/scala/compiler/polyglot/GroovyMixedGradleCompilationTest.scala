package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
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
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
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
import scala.jdk.CollectionConverters.*

@Category(Array(classOf[CompilationTests_Zinc]))
@RunWith(classOf[Parameterized])
class GroovyMixedGradleCompilationTest(jdkVersion: TestJdkVersion) extends ExternalSystemImportingTestCase {

  private var gradleSdk: Sdk = uninitialized

  private var sdk: Sdk = uninitialized

  private var compiler: CompilerTester = uninitialized

  private var mainModule: Module = uninitialized

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

    createProjectSubDirs("src/main/groovy", "src/main/java", "src/main/kotlin", "src/main/scala")
    createProjectSubFile("settings.gradle",
      """rootProject.name = 'groovy-mixed'
        |""".stripMargin)
    createProjectConfig(
      """plugins {
        |    id 'groovy'
        |    id 'java'
        |    id 'org.jetbrains.kotlin.jvm' version '2.3.21'
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

    KotlinDaemonUtil.disableKotlinDaemon(getMyProject)

    val modules = ModuleManager.getInstance(getMyProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    IndexingTestUtil.waitUntilIndexesAreReady(getMyProject)

    mainModule = modules.find(_.getName == "groovy-mixed.main").orNull
    assertNotNull("Could not find module with name 'groovy-mixed.main'", mainModule)

    // The platform no longer puts the Groovy JPS builders on the compile server classpath, so the Groovy sources in
    // the test project fail to compile. Add them ourselves by reflecting into the build process classpath manager and
    // appending the JPS builder jars resolved from the Groovy plugin's lib folder.
    locally {
      val buildManager = ApplicationManager.getApplication.getService(classOf[BuildManager])
      val classpathManagerField = buildManager.getClass.getDeclaredField("myClasspathManager")
      classpathManagerField.setAccessible(true)
      val classpathManager = classpathManagerField.get(buildManager).asInstanceOf[BuildProcessClasspathManager]
      //noinspection ApiStatus
      classpathManager.getBuildProcessPluginsClasspath(getMyProject)
      val field = classpathManager.getClass.getDeclaredField("compileServerPluginsClasspath")
      field.setAccessible(true)
      val originalClasspath = field.get(classpathManager).asInstanceOf[java.util.List[String]].asScala

      val groovyPlugin = PluginManagerCore.getPlugin(PluginId.getId("org.intellij.groovy"))
      assertNotNull("Could not find the Groovy plugin descriptor", groovyPlugin)
      val groovyLibDir = groovyPlugin.getPluginPath.resolve("lib")
      val groovyBuilders = Seq("groovy-jps.jar", "groovy-constants-rt.jar").map(groovyLibDir.resolve(_).toString)
      val newClasspath = originalClasspath ++ groovyBuilders
      field.set(classpathManager, newClasspath.asJava)
    }

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
  def testMixedGroovyCompilation(): Unit = {
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType)
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

private object GroovyMixedGradleCompilationTest extends JdkVersionParameters
