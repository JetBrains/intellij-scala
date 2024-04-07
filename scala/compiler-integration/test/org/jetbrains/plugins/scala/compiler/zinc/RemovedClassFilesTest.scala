package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.VfsUtil
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

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class RemovedClassFilesTest extends ZincTestBase {

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
    createProjectSubFile("src/main/scala/A.scala", "class A { def foo = 5 }")
    createProjectSubFile("src/main/scala/B.scala", "class B")
    createProjectSubFile("src/main/scala/C.scala", "class C")
    createProjectSubFile("src/main/scala/D.scala", "class D")
    createProjectSubFile("src/main/scala/E.scala", "class E")
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

  def testRemoveAllClassFilesAndCompileAgain(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 5)

    val classFileNames = List("A", "B", "C", "D", "E")

    val firstClassFiles = classFileNames.map(findClassFileInRootModule)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    firstClassFiles.foreach(removeFile)

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 5)

    val secondClassFiles = classFileNames.map(findClassFileInRootModule)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val recompiled = firstTimestamps.zip(secondTimestamps).forall { case (a, b) => a < b }
    assertTrue("Not all source files were recompiled", recompiled)
  }

  def testRemoveTwoClassFilesAndCompileAgain(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 5)

    val classFileNames = List("A", "B", "C", "D", "E")

    val firstClassFiles = classFileNames.map(findClassFileInRootModule)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    removeFile(firstClassFiles(2)) // delete C.class

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 1)

    val secondClassFiles = classFileNames.map(findClassFileInRootModule)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val correct = firstTimestamps.zip(secondTimestamps).zipWithIndex.forall {
      case ((x, y), 2) => x < y
      case ((x, y), _) => x == y
    }
    assertTrue(correct)
  }

  def testRemoveClassFileAndEditDependentSource(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 5)

    val classFileNames = List("A", "B", "C", "D", "E")

    val firstClassFiles = classFileNames.map(findClassFileInRootModule)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    removeFile(firstClassFiles.head) // delete A.class

    val bSourcePath = Path.of(getProjectPath).resolve(Path.of("src", "main", "scala", "B.scala"))
    val bSource = VfsUtil.findFileByIoFile(bSourcePath.toFile, true)
    inWriteAction {
      VfsUtil.saveText(bSource, """class B { new A().foo }""")
    }

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 2)

    val secondClassFiles = classFileNames.map(findClassFileInRootModule)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val correct = firstTimestamps.zip(secondTimestamps).zipWithIndex.forall {
      case ((x, y), 0) => x < y
      case ((x, y), 1) => x < y
      case ((x, y), _) => x == y
    }
    assertTrue(correct)
  }
}
