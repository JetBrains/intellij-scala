package org.jetbrains.plugins.scala.compiler.zinc

import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class RemovedClassFilesTest extends ExternalSystemImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  private var rootModule: Module = _

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

    val firstClassFiles = classFileNames.map(findClassFile)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    firstClassFiles.foreach(removeFile)

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 5)

    val secondClassFiles = classFileNames.map(findClassFile)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val recompiled = firstTimestamps.zip(secondTimestamps).forall { case (a, b) => a < b }
    assertTrue("Not all source files were recompiled", recompiled)
  }

  def testRemoveTwoClassFilesAndCompileAgain(): Unit = {
    val messages1 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages1)
    assertCompilingScalaSources(messages1, 5)

    val classFileNames = List("A", "B", "C", "D", "E")

    val firstClassFiles = classFileNames.map(findClassFile)
    val firstTimestamps = firstClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    removeFile(firstClassFiles(2)) // delete C.class

    val messages2 = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages2)
    assertCompilingScalaSources(messages2, 1)

    val secondClassFiles = classFileNames.map(findClassFile)
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

    val firstClassFiles = classFileNames.map(findClassFile)
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

    val secondClassFiles = classFileNames.map(findClassFile)
    val secondTimestamps = secondClassFiles.map(Files.getLastModifiedTime(_).toMillis)

    val correct = firstTimestamps.zip(secondTimestamps).zipWithIndex.forall {
      case ((x, y), 0) => x < y
      case ((x, y), 1) => x < y
      case ((x, y), _) => x == y
    }
    assertTrue(correct)
  }

  private def assertNoErrorsOrWarnings(messages: Seq[CompilerMessage]): Unit = {
    val errorsAndWarnings = messages.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }
    assertTrue(s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}", errorsAndWarnings.isEmpty)
  }

  private def assertCompilingScalaSources(messages: Seq[CompilerMessage], number: Int): Unit = {
    val message = messages.find { message =>
      val text = message.getMessage
      text.contains("compiling") && text.contains("Scala source")
    }.orNull
    assertNotNull("Could not find Compiling Scala sources message", message)
    val expected = s"compiling $number Scala source"
    val text = message.getMessage
    assertTrue(s"Compiling wrong number of Scala sources, expected '$expected', got '$text'", text.contains(expected))
  }

  private def findClassFile(name: String): Path = {
    val cls = compiler.findClassFile(name, rootModule)
    assertNotNull(s"Could not find compiled class file $name", cls)
    cls.toPath
  }

  private def removeFile(path: Path): Unit = {
    val virtualFile = VfsUtil.findFileByIoFile(path.toFile, true)
    inWriteAction {
      virtualFile.delete(null)
    }
  }
}
