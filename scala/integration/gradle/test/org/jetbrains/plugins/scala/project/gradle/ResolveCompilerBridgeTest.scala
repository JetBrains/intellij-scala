package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt}
import org.junit.Assert.{assertEquals, assertNotNull}

class ResolveCompilerBridgeTest extends ExternalSystemImportingTestCase {

  private val scalaVersion: String = ScalaVersion.Latest.Scala_3_RC.minor

  private var sdk: Sdk = _

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

    sdk = SmartJDKLoader.getOrCreateJDK(LanguageLevel.JDK_17)

    createProjectSubFile("settings.gradle",
      """rootProject.name = 'resolve-compiler-bridge'
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
        |    implementation 'org.scala-lang:scala3-library_3:$scalaVersion'
        |}
        |""".stripMargin)

    importProject(false)
  }

  override def tearDown(): Unit = try {
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
  } finally {
    super.tearDown()
  }

  def testResolveCompilerBridge(): Unit = {
    val project = myProject
    val scalaSdk = project.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk match {
      case ex: LibraryEx => ex.properties
    }

    val compilerBridge = properties.compilerBridgeBinaryJar.orNull
    assertNotNull("Scala 3 compiler bridge not configured", compilerBridge)

    assertEquals(s"scala3-sbt-bridge-$scalaVersion.jar", compilerBridge.getName)
  }
}
