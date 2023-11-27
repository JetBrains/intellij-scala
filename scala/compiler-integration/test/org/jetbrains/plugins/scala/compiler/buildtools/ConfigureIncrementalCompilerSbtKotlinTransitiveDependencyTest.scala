package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

@Category(Array(classOf[CompilationTests]))
class ConfigureIncrementalCompilerSbtKotlinTransitiveDependencyTest extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

  override lazy val getCurrentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.jdk = sdk.getName
    settings
  }

  override def getExternalSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def getExternalSystemConfigFileName: String = Sbt.BuildFile

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

    createProjectSubDirs("project", "src/main/scala")
    createProjectSubFile("project/build.properties",
      """sbt.version=1.9.7
        |""".stripMargin)
    createProjectSubFile("src/main/scala/HelloWorld.scala",
      """object HelloWorld {
         |  def main(args: Array[String]): Unit = {
         |    println("Hello, world!")
         |  }
         |}
        |""".stripMargin)
    createProjectConfig(
      """scalaVersion := "2.13.12"
        |
        |// This library has a transitive dependency on the Kotlin standard library
        |libraryDependencies += "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.31.0"
        |""".stripMargin)
  }

  override def tearDown(): Unit = try {
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
  } finally {
    super.tearDown()
  }

  def testTransitiveDependencyOnKotlinStandardLibrary(): Unit = {
    importProject(false)

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType)
  }
}
