package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{LibraryExt, ModuleExt}
import org.jetbrains.plugins.scala.projectHighlighting.base.{GithubRepositoryWithRevision, SbtProjectHighlightingDownloadingFromGithubTestBase}
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, RevertableChange}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, SlowTests}
import org.junit.experimental.categories.Category

import java.net.{URL, URLClassLoader}

/**
 * This test:
 * - downloads dotty project from GitHub
 * - then compiles it
 * - and then loads Main class
 */
@Category(Array(classOf[SlowTests]))
abstract class Scala3ExampleProjectCompilationTestBase(
  incrementalityType: IncrementalityType,
  useCompileServer: Boolean
) extends SbtProjectHighlightingDownloadingFromGithubTestBase
    with ScalaSdkOwner {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("scala", "scala3-example-project", revision = "main")

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq.empty

  private var compiler: CompilerTester = _

  private var revertible: RevertableChange = RevertableChange.NoOpRevertableChange

  override def setUp(): Unit = {
    super.setUp()

    revertible = CompilerTestUtil.withEnabledCompileServer(useCompileServer)
    revertible.applyChange()
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementalityType
    compiler = new CompilerTester(myProject, java.util.List.of(getMainModule), null, false)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    CompileServerLauncher.stopServerAndWait()
    val table = ProjectJdkTable.getInstance
    inWriteAction {
      table.getAllJdks.foreach(table.removeJdk)
    }
  } finally {
    compiler = null
    revertible.revertChange()
    super.tearDown()
  }

  def testDownloadCompileLoadClass(): Unit = {
    val compilerMessages = compiler.rebuild()

    //scala3-example-project can itself contain some warnings (e.g. deprecations)
    //because the project is not perfect
    //But we at least don't expect any errors during compilation
    val allowWarningsDuringCompilation = true
    compilerMessages.assertNoProblems(allowWarningsDuringCompilation)

    val urls = (librariesUrls + targetUrl).toArray
    val classLoader = new URLClassLoader(urls, null)

    try {
      classLoader.loadClass("Main")
    } catch {
      case ex: ClassNotFoundException =>
        throw new AssertionError("Couldn't load class `Main`. Looks like test project wasn't successfully compiled. See build output for the details.", ex)
    }
  }

  private def librariesUrls: Set[URL] =
    getMainModule.libraries.flatMap(_.jarUrls)

  private def targetUrl: URL =
    new URL(CompilerModuleExtension.getInstance(getMainModule).getCompilerOutputUrl + "/")

  private def getMainModule: Module =
    getModule(projectName)
}

class Scala3ExampleProjectCompilationTest_IdeaIncrementalityType
  extends Scala3ExampleProjectCompilationTestBase(IncrementalityType.IDEA, useCompileServer = false)

class Scala3ExampleProjectCompilationTest_SbtIncrementalityType
  extends Scala3ExampleProjectCompilationTestBase(IncrementalityType.SBT, useCompileServer = false)
