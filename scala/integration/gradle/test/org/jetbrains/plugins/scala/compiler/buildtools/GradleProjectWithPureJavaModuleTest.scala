package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.JdkVersionParameters
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.gradle.GradleTestUtil
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@RunWith(classOf[Parameterized])
class GradleProjectWithPureJavaModuleTest(jdkVersion: TestJdkVersion) extends ExternalSystemImportingTestCase {

  private var gradleSdk: Sdk = uninitialized

  private var sdk: Sdk = uninitialized

  private var compiler: CompilerTester = uninitialized

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

    createProjectSubDirs("module1/src/main/java", "module2/src/main/scala")
    createProjectSubFile("settings.gradle",
      """rootProject.name = 'root'
        |include 'module1', 'module2'
        |""".stripMargin)
    createProjectSubFile("module1/build.gradle",
      """plugins {
        |    id 'java'
        |}
        |
        |java {
        |    sourceCompatibility = JavaVersion.VERSION_1_8
        |    targetCompatibility = JavaVersion.VERSION_1_8
        |}
        |
        |group = 'org.example'
        |version = '1.0-SNAPSHOT'
        |""".stripMargin)
    createProjectSubFile("module2/build.gradle",
      """plugins {
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
        |    implementation project(':module1')
        |    implementation 'org.scala-lang:scala-library:2.13.12'
        |}
        |""".stripMargin)
    createProjectSubFile("module1/src/main/java/Greeter.java",
      """interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends Greeter {
        |  def greeting: String = "Hello, world!"
        |}
        |""".stripMargin)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction {
      val table = ProjectJdkTable.getInstance()
      Seq(sdk, gradleSdk).foreach(table.removeJdk)
    }
  } finally {
    super.tearDown()
  }

  @Category(Array(classOf[CompilationTests_Zinc]))
  @Test
  def importAndCompile_Zinc(): Unit = {
    runImportAndCompileTest(IncrementalityType.SBT)
  }

  @Category(Array(classOf[CompilationTests_IDEA]))
  @Test
  def importAndCompile_IDEA(): Unit = {
    runImportAndCompileTest(IncrementalityType.IDEA)
  }

  private def runImportAndCompileTest(incrementality: IncrementalityType): Unit = {
    importProject(false)

    ScalaCompilerConfiguration.instanceIn(getMyProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getMyProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))
    compiler = new CompilerTester(getMyProject, java.util.Arrays.asList(modules*), null, false)

    val messages = compiler.make()
    val errorsAndWarnings = messages.asScala.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }

    assertTrue(
      s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}",
      errorsAndWarnings.isEmpty
    )

    val module1 = modules.find(_.getName.contains("module1.main")).orNull
    assertNotNull("Could not find module with name 'module1.main'", module1)
    val module2 = modules.find(_.getName.contains("module2.main")).orNull
    assertNotNull("Could not find module with name 'module2.main'", module2)

    val greeter = compiler.findClassFile("Greeter", module1)
    assertNotNull("Could not find compiled class file Greeter", greeter)

    val helloWorldGreeter = compiler.findClassFile("HelloWorldGreeter", module2)
    assertNotNull("Could not find compiled class file HelloWorldGreeter", helloWorldGreeter)

    val helloWorldGreeterModule = compiler.findClassFile("HelloWorldGreeter$", module2)
    assertNotNull("Could not find compiled class file HelloWorldGreeter$", helloWorldGreeterModule)
  }
}

private object GradleProjectWithPureJavaModuleTest extends JdkVersionParameters
