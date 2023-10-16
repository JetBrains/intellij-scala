package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.CompilerTester
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, RevertableChange, TestUtils}
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertTrue, fail}

import java.io.File
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.IteratorHasAsScala

class CompileSimpleProjectWithSemanticDbTest extends SbtExternalSystemImportingTestLike {

  override protected lazy val getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/compilation/projects/${getTestName(true)}"

  @throws[Exception]
  override protected def setUpFixtures(): Unit = {
    //Copied from `com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase.setUpFixtures`
    //But here we pass custom project path instead of using the default temporary empty project path
    //(created in com.intellij.testFramework.fixtures.impl.HeavyIdeaTestFixtureImpl.generateProjectPath)
    //This is needed because `org.jetbrains.jps.incremental.scala.data.CompilerDataFactory.semanticDbOptionsFor`
    //needs the correct project path to be equal to the project actual root in order correct semanticDb target folder is calculated later
    //(it's done in dotty.tools.dotc.semanticdb.ExtractSemanticDB#write)
    val testProjectDir = new File(getTestProjectPath)
    val projectParentFolder = testProjectDir.getParentFile.toPath
    val projectName = testProjectDir.getName
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(projectName, projectParentFolder, true).getFixture
    myTestFixture.setUp()
  }

  def testWithSemanticDb_Scala3(): Unit = {
    buildProjectAndCheckThatNoSemanticDbIsGeneratedInSrcFolder()
  }

  def testWithSemanticDb_Scala2(): Unit = {
    buildProjectAndCheckThatNoSemanticDbIsGeneratedInSrcFolder()
  }

  private def buildProjectAndCheckThatNoSemanticDbIsGeneratedInSrcFolder(): Unit = {
    importProject(false)
    buildProject()

    val projectRoot = myProjectRoot.toNioPath

    val srcFolder = projectRoot.resolve("src")
    assertTrue("src folder not found", srcFolder.toFile.exists())
    val targetFolder = projectRoot.resolve("target")
    assertTrue("target folder not found", targetFolder.toFile.exists())

    val nonScalaFilesInSrc = getRecursiveFilesIn(srcFolder).map(projectRoot.relativize)
    assertNotContains[Path](
      s"src must contain `.scala` files only ($projectRoot)",
      nonScalaFilesInSrc,
      path => !path.toString.endsWith(".scala")
    )

    val filesInTarget = getRecursiveFilesIn(targetFolder).map(projectRoot.relativize)
    assertContains[Path](
      s"target folder must contain compiled `.class` files ($projectRoot)",
      filesInTarget,
      _.toString.endsWith(".class")
    )
    assertContains[Path](
      s"target folder must contain compiled `.semanticdb` files ($projectRoot)",
      filesInTarget,
      _.toString.endsWith(".semanticdb")
    )
  }

  private def assertContains[T](message: String, seq: Seq[T], condition: T => Boolean): Unit = {
    if (!seq.exists(condition)) {
      fail(s"$message\n${seq.mkString("\n")}")
    }
  }

  private def assertNotContains[T](message: String, seq: Seq[T], condition: T => Boolean): Unit = {
    if (seq.exists(condition)) {
      fail(s"$message\n${seq.mkString("\n")}")
    }
  }

  private def getRecursiveFilesIn(path: Path): Seq[Path] =
    Files.walk(path).iterator().asScala.filter(_.toFile.isFile).toSeq

  private def buildProject(): Unit = {
    val settings = ScalaCompileServerSettings.getInstance()
    val compileServerWorkingDir = Files.createTempDirectory("scala-compile-server-working-dir").toFile.getAbsoluteFile

    //We need to use a completely-unrelated working directory for the compile server in order teh test tests the correct thing.
    //In `dotty.tools.dotc.semanticdb.ExtractSemanticDB#write` when `SourceFile.relativePath` is calculated
    //for relative path of a source file it uses working directory by default (pwd ~ '.', if no options were passed to the compiler)
    //If the directory is a parent folder for the project folder it will calculate relative path correctly
    //and we won't be able to reproduce SCL-20779 or SCL-17519
    val withModifiedCompileServerWorkingDir = RevertableChange.withModifiedSetting[String](
      settings.CUSTOM_WORKING_DIR_FOR_TESTS,
      settings.CUSTOM_WORKING_DIR_FOR_TESTS = _,
      compileServerWorkingDir.getAbsolutePath
    )
    val revertible = CompilerTestUtil.withEnabledCompileServer(true) |+| withModifiedCompileServerWorkingDir
    revertible.run {
      //uncomment to debug JPS process
      //BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
      //Registry.get("compiler.process.debug.port").setValue(5432)

      val compiler = new CompilerTester(myProject, java.util.List.of(myTestFixture.getModule), null, false)
      try {
        compiler.rebuild().assertNoProblems()
      } finally {
        compiler.tearDown()
        ScalaCompilerTestBase.stopAndWait()

        val table = ProjectJdkTable.getInstance
        inWriteAction {
          table.getAllJdks.foreach(table.removeJdk)
        }
      }
    }
  }
}
