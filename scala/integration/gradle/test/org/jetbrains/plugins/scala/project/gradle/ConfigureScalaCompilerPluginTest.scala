package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{CompilerTester, IndexingTestUtil}
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests]))
class ConfigureScalaCompilerPluginTest extends ExternalSystemImportingTestCase {

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

  override def getTestsTempDir: String = getTestName(true)

  override def getExternalSystemConfigFileName: String = GradleConstants.DEFAULT_SCRIPT_NAME

  override def setUp(): Unit = {
    super.setUp()

    GradleTestUtil.setupGradleHome(getProject)

    ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED = false

    sdk = SmartJDKLoader.getOrCreateJDK(LanguageLevel.JDK_17)

    createProjectSubDirs("src/main/scala")
    createProjectSubFile("settings.gradle",
      """rootProject.name = 'configure-scala-compiler-plugin'
        |""".stripMargin)
    createProjectSubFile("src/main/scala/Main.scala",
      """object Main {
        |  def counts: Either[String, (Int, Int)] = ???
        |
        |  def main(args: Array[String]): Unit = {
        |    for {
        |      (x, y) <- counts
        |    } yield x + y
        |  }
        |}
        |""".stripMargin)
    createProjectConfig(
      s"""plugins {
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
         |    implementation 'org.scala-lang:scala-library:2.13.14'
         |    scalaCompilerPlugins 'com.olegpy:better-monadic-for_2.13:0.3.1'
         |}
         |""".stripMargin)

    importProject(false)

    val modules = ModuleManager.getInstance(getProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    mainModule = modules.find(_.getName == "configure-scala-compiler-plugin.main").get
    compiler = new CompilerTester(getProject, java.util.Arrays.asList(modules: _*), null, false)

    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
    ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED = true
  } finally {
    super.tearDown()
  }

  def testConfigureScalaCompilerPlugin(): Unit = {
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(getProject).getSettingsForModule(mainModule)

    assertEquals(1, compilerConfiguration.plugins.size)
    val plugin = compilerConfiguration.plugins.head
    assertTrue(plugin.endsWith("better-monadic-for_2.13-0.3.1.jar"))

    assertTrue(mainModule.betterMonadicForPluginEnabled)

    val messages = compiler.make().asScala.toList
    val errorsAndWarnings = messages.filter(_.getCategory == CompilerMessageCategory.ERROR)
    assertTrue(errorsAndWarnings.isEmpty)
  }
}
