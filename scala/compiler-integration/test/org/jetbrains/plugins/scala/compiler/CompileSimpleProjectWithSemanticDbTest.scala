package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, RevertableChange, TestUtils}
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.{IdeaProjectFixtureOptions, TestProjectCopyOptions}
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertTrue, fail}
import org.junit.experimental.categories.Category

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.IteratorHasAsScala

@Category(Array(classOf[SlowTests]))
class CompileSimpleProjectWithSemanticDbTest extends SbtExternalSystemImportingTestLike {

  override protected lazy val getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/compilation/projects/${getTestName(true)}"

  // `CompilerDataFactory.semanticDbOptionsFor` calculates the SemanticDB target relative to the IDEA project path.
  // Keep the opened IDEA project path equal to the sbt project root used by the test.
  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def getIdeaProjectFixtureOptions: IdeaProjectFixtureOptions =
    super.getIdeaProjectFixtureOptions.copy(useTestProjectAsIdeaProjectRoot = true)

  def testWithSemanticDb_Scala3(): Unit = {
    buildProjectAndCheckThatNoSemanticDbIsGeneratedInSrcFolder()

    val module = this.getMyTestFixture.getProject.modules.find(m => !m.isBuildModule && m.isMain).get
    assertTrue(
      "Custom compiler bridge is expected to be non empty for Scala 3 language in SBT projects (see SCL-21741)",
      module.customScalaCompilerBridgeJar.nonEmpty
    )
  }

  def testWithSemanticDb_Scala2(): Unit = {
    buildProjectAndCheckThatNoSemanticDbIsGeneratedInSrcFolder()
  }

  private def buildProjectAndCheckThatNoSemanticDbIsGeneratedInSrcFolder(): Unit = {
    importProject(false)
    buildProject()
    assertNoSemanticDbIsGeneratedInSrcFolder()
  }

  private def assertNoSemanticDbIsGeneratedInSrcFolder(): Unit = {
    val projectRoot = getMyProjectRoot.toNioPath

    val srcFolder = projectRoot.resolve("src")
    val targetFolder = projectRoot.resolve("target")
    assertTrue("src folder not found", srcFolder.exists)
    assertTrue("target folder not found", targetFolder.exists)

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
    Files.walk(path).iterator().asScala.filter(_.isRegularFile).toSeq

  private def buildProject(): Unit = {
    val settings = ScalaCompileServerSettings.getInstance()
    val compileServerWorkingDir = Files.createTempDirectory("scala-compile-server-working-dir")

    //We need to use a completely unrelated working directory for the compiler server in order the test tests the correct thing.
    //In `dotty.tools.dotc.semanticdb.ExtractSemanticDB#write` when `SourceFile.relativePath` is calculated
    //for a relative path of a source file it uses working directory by default (pwd ~ '.', if no options were passed to the compiler)
    //If the directory is a parent folder for the project folder, it will calculate the relative path correctly,
    //and we won't be able to reproduce SCL-20779 or SCL-17519
    val withModifiedCompileServerWorkingDir = RevertableChange.withModifiedSetting[String](
      settings.CUSTOM_WORKING_DIR_FOR_TESTS,
      settings.CUSTOM_WORKING_DIR_FOR_TESTS = _,
      compileServerWorkingDir.toCanonicalPath.toString
    )
    val revertible = CompilerTestUtil.withEnabledCompileServer(true) |+| withModifiedCompileServerWorkingDir
    revertible.run {
      //uncomment to debug JPS process
      //BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
      //Registry.get("compiler.process.debug.port").setValue(5432)

      val compiler = new CompilerTester(getMyProject, java.util.List.of(getMyTestFixture.getModule), null, false)
      try {
        compiler.rebuild().assertNoProblems()
      } finally {
        compiler.tearDown()
        CompileServerLauncher.stopServerAndWait()

        val table = ProjectJdkTable.getInstance
        inWriteAction {
          table.getAllJdks.foreach(table.removeJdk)
        }
      }
    }
  }
}
