package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[CompilationTests]))
class ConfigureIncrementalCompilerGradleTest extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

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

    sdk = {
      val jdkVersion =
        Option(System.getProperty("filter.test.jdk.version"))
          .map(TestJdkVersion.valueOf)
          .getOrElse(TestJdkVersion.JDK_17)
          .toProductionVersion

      SmartJDKLoader.getOrCreateJDK(jdkVersion)
    }

    createProjectSubDirs("module1/src/main/java", "module2/src/main/scala", "module3/src/main/kotlin")
    createProjectSubFile("settings.gradle",
      """rootProject.name = 'root'
        |include 'module1', 'module2', 'module3'
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
        |    implementation 'org.scala-lang:scala-library:2.13.12'
        |}
        |""".stripMargin)
    createProjectSubFile("module3/build.gradle",
      """plugins {
        |    id 'org.jetbrains.kotlin.jvm' version '1.9.0'
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
        |    testImplementation 'org.jetbrains.kotlin:kotlin-test'
        |}
        |""".stripMargin)
    createProjectSubFile("module1/src/main/java/Foo.java", "class Foo {}".stripMargin)
    createProjectSubFile("module2/src/main/scala/Bar.scala", "class Bar".stripMargin)
    createProjectSubFile("module3/src/main/kotlin/Baz.kt", "class Baz {}".stripMargin)
  }

  override def tearDown(): Unit = try {
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
  } finally {
    super.tearDown()
  }

  def testIncrementalCompilerSetUp(): Unit = {
    importProject(false)

    val modules = ModuleManager.getInstance(myProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    assertEquals(IncrementalityType.IDEA, ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType)
  }
}
