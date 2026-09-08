package org.jetbrains.plugins.scala.projectHighlighting.scalaLibrary

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{ModuleRootModificationUtil, ProjectRootManager}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.{FixturesKt, TestFixture}
import com.intellij.testFramework.{IndexingTestUtil, OpenProjectTaskBuilder}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.annotator.HighlightingAdvisor
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{ScalaSDKLoader, SmartJDKLoader}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider.ScClsFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.projectHighlighting.base.AllProjectHighlightingTest
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.{Assumptions, BeforeAll, Test, TestInstance}

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Runs [[org.jetbrains.plugins.scala.annotator.ScalaAnnotator]] over every source file of the Scala library and
 * compares the reported errors against [[filesWithProblems]].
 *
 * The project is built once per test class: [[TestInstance.Lifecycle.PER_CLASS]] makes the instance fixtures below
 * class-level, so `TestFixtureExtension` initializes them in `beforeAll` and tears them down in `afterAll`
 * (see `TestFixtureExtension.beforeAll`/`beforeEach`). Both test methods therefore share one project.
 *
 * The files are annotated in parallel batches. Note that this is not JUnit's parallel execution - the whole thing is
 * a single test method that fans out internally.
 *
 * Each batch accumulates its own errors and the results are merged after the join, so the parallel phase shares no
 * mutable state and needs no synchronization; [[HighlightingProgressReporter]], which is not thread safe, is then fed
 * sequentially and stays the one place where results are aggregated and reported.
 *
 * @param scalaVersion the Scala version whose library is highlighted. Replaces the JUnit 3 `supportedIn` hook: it is
 *                     compared against a globally configured version (if any) to skip the test, see [[setUpScalaLibrary]].
 */
@TestApplication
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ScalaLibraryHighlightingTest(scalaVersion: ScalaVersion) {

  protected def customScalaSdkLoader: ScalaSDKLoader = ScalaSDKLoader(includeScalaLibrarySources = true)

  protected def filesWithProblems: Map[String, Set[TextRange]] = Map()

  protected def scalaLibraryJarName: String = "scala-library"

  protected def projectJdk: Sdk = SmartJDKLoader.createJdk(LanguageLevel.JDK_17)

  /** The number of batches the source files are split into, each annotated by its own future. */
  protected def numBatches: Int = Runtime.getRuntime.availableProcessors

  /**
   * The fixtures must be defined as class members in order to be initialized and torn down by the
   * `TestFixtureExtension` installed via `@TestApplication`.
   *
   * The project must be opened (`openAfterCreation = true`) so that the project startup activities run. Without them
   * the Scala synthetic classes are never registered and standard types do not resolve.
   */
  private val projectFixture: TestFixture[Project] =
    FixturesKt.projectFixture(FixturesKt.tempPathFixture(), OpenProjectTaskBuilder().build(), true)
  private val moduleFixture: TestFixture[Module] =
    FixturesKt.moduleFixture(projectFixture, "main", null)
  private val disposableFixture: TestFixture[Disposable] =
    FixturesKt.disposableFixture()

  private def getProject: Project = projectFixture.get()
  private def getModule: Module = moduleFixture.get()

  /**
   * Type-aware highlighting is off for library sources by default, so without the first change the annotator would
   * report almost nothing. The registry key carries over from `ScalaLightCodeInsightFixtureTestCase.setUp`, which
   * this test no longer inherits.
   */
  private val revertableChanges: RevertableChange =
    RevertableChange.withModifiedSetting[Boolean](
      HighlightingAdvisor.typeAwareHighlightingForScalaLibrarySourcesEnabled,
      HighlightingAdvisor.typeAwareHighlightingForScalaLibrarySourcesEnabled = _,
      true
    ) |+| RevertableChange.withModifiedRegistryValue("ast.loading.filter", true)

  @BeforeAll
  def setUpScalaLibrary(): Unit = {
    ScalaSdkOwner.globalConfiguredScalaVersion.foreach: configuredVersion =>
      Assumptions.assumeTrue(configuredVersion == scalaVersion, s"Not supported in Scala version $configuredVersion")

    val project = getProject
    val module = getModule

    // Neither SmartJDKLoader.createJdk nor createFilteredJdk register the JDK themselves, so it is registered here
    // with the project as the disposable parent - no manual removal needed.
    val jdk = projectJdk
    WriteAction.runAndWait: () =>
      val jdkTable = ProjectJdkTable.getInstance()
      if !jdkTable.getAllJdks.contains(jdk) then jdkTable.addJdk(jdk, project)
      ProjectRootManager.getInstance(project).setProjectSdk(jdk)
      ModuleRootModificationUtil.setModuleSdk(module, jdk)

    // Registers the library source roots, which is what makes ProjectFileIndex.isInLibrarySource true for them,
    // and marks the module as a Scala module.
    customScalaSdkLoader.init(using module, scalaVersion)

    revertableChanges.applyChange(disposableFixture.get())

    // Must complete before the fan-out, so that the worker read actions don't compete with indexing write actions.
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  @Test
  def highlightScalaLibrary(): Unit = {
    val reporter = HighlightingProgressReporter.newInstance(getClass.getSimpleName, filesWithProblems)
    AllProjectHighlightingTest.warnIfUsingRandomizedTests(reporter)

    val project = getProject
    val sourceRoots = customScalaSdkLoader.scalaLibrarySources(using scalaVersion)
    try {
      val allFiles = sourceRoots.flatMap { sourceRoot =>
        val scalaFiles = ScalaLibraryHighlightingTest.findAllScalaFiles(sourceRoot).sortBy(_.getPath)
        assertTrue(scalaFiles.nonEmpty, s"Couldn't find any scala source files in $sourceRoot")
        scalaFiles.map(file => VfsUtilCore.getRelativePath(file, sourceRoot) -> file)
      }

      val fileManager = PsiManager.getInstance(project).asInstanceOf[PsiManagerEx].getFileManager

      given ExecutionContext = ExecutionContext.fromExecutorService(AppExecutorUtil.getAppExecutorService)

      // Deal the files round-robin rather than in contiguous chunks: the list is sorted by path, so contiguous chunks
      // would pile all of scala/collection into a single batch and leave it as the straggler.
      val batches = allFiles.zipWithIndex.groupMap(_._2 % numBatches)(_._1).values.toSeq

      val futures = batches.map { batch =>
        Future {
          batch.map { (fileRelativePath, file) =>
            // On the EDT the JUnit 3 predecessor had read access implicitly; on the worker threads it must be explicit.
            // Per file rather than per batch, so that no thread holds a read action for the whole run.
            val errors = inReadAction {
              AllProjectHighlightingTest.collectScalaFileErrors(fileManager.findFile(file))
            }
            fileRelativePath -> errors
          }
        }
      }
      val errorsByFile = Await.result(Future.sequence(futures), 10.minutes).flatten.toMap

      // The reporter is fed here rather than from the batches: it is not thread safe, and it does not need to be.
      // Reporting in the original (path-sorted) order also keeps its output identical to the sequential version,
      // including the per-file error throttle, which relies on all errors of a file arriving together.
      val filesTotal = allFiles.size
      allFiles.zipWithIndex.foreach { case ((fileRelativePath, _), fileIndex) =>
        reporter.notifyHighlightingProgress(fileIndex, filesTotal, fileRelativePath)
        errorsByFile(fileRelativePath).foreach { error =>
          reporter.reportError(fileRelativePath, error.range, error.message)
        }
      }

      reporter.reportFinalResults()
    } finally {
      System.err.println(
        s"""### sourceRoots:
           |${sourceRoots.mkString("\n")}""".stripMargin
      )
    }
  }

  @Test
  def allSourcesAreFoundByRelativeFile(): Unit = inReadAction {
    given Project = getProject

    val classFilesFromScalaLibrary = for {
      className <- ScalaIndexKeys.ALL_CLASS_NAMES.allKeys
      psiClass <- ScalaIndexKeys.ALL_CLASS_NAMES.elements(className, GlobalSearchScope.allScope(getProject))
      file <- psiClass.getContainingFile.asOptionOf[ScClsFileImpl]
      if file.getVirtualFile.getPath.contains(scalaLibraryJarName)
    } yield file

    assertTrue(
      classFilesFromScalaLibrary.size > 100,
      s"Too few class files found in scala-library: ${classFilesFromScalaLibrary.size}"
    )

    classFilesFromScalaLibrary.foreach { file =>
      val sourceFile = file.findSourceByRelativePath
      assertTrue(
        sourceFile.nonEmpty,
        s"Source file for ${file.getVirtualFile.getPath} was not found by relative path"
      )
    }
  }
}

object ScalaLibraryHighlightingTest {

  private def findAllScalaFiles(sourceRoot: VirtualFile): Seq[VirtualFile] = {
    val allScalaFiles = mutable.ArrayBuffer.empty[VirtualFile]
    VfsUtilCore.processFilesRecursively(
      sourceRoot,
      (vFile: VirtualFile) => {
        if (vFile.getFileType == ScalaFileType.INSTANCE) {
          allScalaFiles += vFile
        }
        true
      }
    )
    allScalaFiles.toSeq
  }
}
