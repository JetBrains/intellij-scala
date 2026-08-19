package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt, ReplClasspath}
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests2}
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.compiletime.uninitialized

@Category(Array(classOf[SlowTests2]))
@RunWith(classOf[JUnit4])
class ResolveReplClasspathTest extends ExternalSystemImportingTestCase:

  private val scalaVersion: String = ScalaVersion.Latest.Scala_3_8.minor

  private var sdk: Sdk = uninitialized

  override lazy val getCurrentExternalProjectSettings: GradleProjectSettings =
    val settings = new GradleProjectSettings().withQualifiedModuleNames()
    settings.setGradleJvm(sdk.getName)
    settings.setDelegatedBuild(false)
    settings

  override def getExternalSystemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  override def getTestsTempDir: String = getTestName(true)

  override def getExternalSystemConfigFileName: String = GradleConstants.DEFAULT_SCRIPT_NAME

  override def setUp(): Unit =
    super.setUp()

    GradleTestUtil.setupGradleHome(getMyProject)

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
         |${GradleTestUtil.repositoriesBlock}
         |
         |dependencies {
         |    implementation 'org.scala-lang:scala3-library_3:$scalaVersion'
         |}
         |""".stripMargin)

    importProject(false)
  end setUp

  override def tearDown(): Unit =
    try inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
    finally super.tearDown()

  @Test
  def resolveReplClasspath(): Unit =
    val project = getMyProject
    val scalaSdk = project.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk.asInstanceOf[LibraryEx].properties

    val replClasspath = properties.replClasspath

    assertTrue("The REPL classpath was not configured correctly", replClasspath.isInstanceOf[ReplClasspath.Provided])
    val paths = replClasspath.asPaths
    assertTrue("The REPL classpath is empty", paths.nonEmpty)
    val scala3ReplJar = paths.find(_.getFileName.toString == s"scala3-repl_3-$scalaVersion.jar")
    assertTrue("Could not find the scala3-repl_3 jar on the REPL classpath", scala3ReplJar.isDefined)
