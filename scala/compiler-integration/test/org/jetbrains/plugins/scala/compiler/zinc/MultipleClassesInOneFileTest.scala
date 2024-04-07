package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import java.nio.file.Files
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class MultipleClassesInOneFileTest extends ZincTestBase {

  override lazy val getCurrentExternalProjectSettings: SbtProjectSettings = {
    val settings = new SbtProjectSettings()
    settings.jdk = sdk.getName
    settings
  }

  override def getTestsTempDir: String = this.getClass.getSimpleName

  override def setUp(): Unit = {
    super.setUp()

    sdk = {
      val jdkVersion =
        Option(System.getProperty("filter.test.jdk.version"))
          .map(TestJdkVersion.valueOf)
          .getOrElse(TestJdkVersion.JDK_17)
          .toProductionVersion

      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    createProjectSubDirs("project", "src/main/scala")
    createProjectSubFile("project/build.properties", "sbt.version=1.9.7")
    createProjectSubFile("src/main/scala/foo.scala",
      """class Foo
        |class Bar
        |class Baz
        |""".stripMargin)
    createProjectConfig(
      """lazy val root = project.in(file("."))
        |  .settings(
        |    scalaVersion := "2.13.12"
        |  )
        |""".stripMargin)

    importProject(false)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT

    val modules = ModuleManager.getInstance(myProject).getModules
    rootModule = modules.find(_.getName == "root").orNull
    assertNotNull("Could not find module with name 'root'", rootModule)
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  override def tearDown(): Unit = try {
    CompileServerLauncher.stopServerAndWait()
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
  } finally {
    super.tearDown()
  }

  def testRemoveOneClassFileAndCompileAgain(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 1)

    val classFileNames = List("Foo", "Bar", "Baz")

    val firstClassFiles = classFileNames.map(findClassFileInRootModule)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    removeFile(firstClassFiles(1)) // remove Bar.class

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 1)

    val secondClassFiles = classFileNames.map(findClassFileInRootModule)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val recompiled = firstTimestamps.zip(secondTimestamps).forall { case (x, y) => x < y }
    assertTrue("Not all source files were recompiled", recompiled)
  }

}
