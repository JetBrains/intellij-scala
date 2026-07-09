package org.jetbrains.bsp.project.importing

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalProjectInfo, Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener, ExternalSystemTaskType}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.manage.{ProjectDataService, WorkspaceDataService}
import com.intellij.openapi.externalSystem.service.project.{IdeModifiableModelsProvider, ProjectDataManager}
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{JavaModuleTestCase, PlatformTestUtil, ServiceContainerUtil}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.bsp.{BSP, BspJdkUtil}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.plugins.scala.util.{CollectingLoggedMessagesProcessor, TestUtils}
import org.jetbrains.sbt.project.fixture.TestProjectJdkHolder
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertTrue}

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CompletableFuture, ConcurrentLinkedQueue}
import java.util.{Collection as JCollection, List as JList}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

/**
 * Exercises the BSP project-open/import path against a real project lifecycle close.
 * See https://youtrack.jetbrains.com/issue/SCL-24439
 *
 * The issue in SCL-24439 was not just "a future was cancelled". The user action was closing an IDE project while
 * BSP import was still initializing a build server, so the important test has to start from the same public boundary:
 *  1. open/link a project through [[BspOpenProjectProvider]]
 *  2. let the provider generate the sbt BSP connection file
 *  3. let import start
 *  4. then close the IDE project while initialization is still in progress
 *
 * The test data project is a minimal real sbt build. Its `Global / onLoad` hook uses marker files for two distinct sbt loads:
 *  1. The first load happens while [[org.jetbrains.bsp.project.importing.setup.SbtConfigSetup]] generates `.bsp/sbt.json`;
 *     that load consumes a one-shot skip marker and must finish quickly.
 *  2. The next load is the actual BSP import load; it creates a marker and waits until the test closes the IDE project and writes a release marker.
 *     The recorded load counts make preview/config/import ordering changes fail with diagnostics instead of timing out silently.
 *
 * A second, narrower test covers the post-resolve callback race. It disposes the IDE project before [[BspOpenProjectProvider.FinalImportCallback]]
 * receives a resolved model and asserts that the callback never reaches [[ProjectDataManager.importData]].
 * That makes the `ProjectDataManager`/`ContainerDisposedException`` path deterministic
 * without trying to hit an inherently narrow timing window inside platform import internals.
 *
 * This class intentionally extends [[JavaModuleTestCase]]. Lighter code-insight fixtures do not model enough of the
 * project/module lifecycle used by external-system project opening. Generic external-system and sbt import base classes
 * are useful for successful import assertions, but they drive imports through test helpers and own the project
 * root/lifetime; this scenario needs to close the test project itself as the action under test. The smaller BSP provider
 * tests cover setup and provider decisions, not the close-during-refresh lifecycle race.
 */
class BspOpenProjectProviderCloseDuringImportTest extends JavaModuleTestCase {

  private val SlowSbtBspProjectName = "bspCloseDuringImport"
  private val TestDataProjectPath = TestUtils.getTestDataDir / "sbt" / "projects" / SlowSbtBspProjectName

  private val testProjectJdk = new TestProjectJdkHolder(LanguageLevel.JDK_17)

  override protected def setUp(): Unit = {
    super.setUp()
    testProjectJdk.setUp()
    testProjectJdk.setAsProjectJdk(getProject)
  }

  override protected def tearDown(): Unit =
    try {
      testProjectJdk.tearDown()
    } finally {
      super.tearDown()
    }

  override protected def runInDispatchThread(): Boolean = false

  def testClosingProjectDuringBspSessionInitializationCancelsWithoutExecutionError(): Unit = {
    // Prepare a real BSP project-open/import flow that can be closed mid-import.
    val project = getProject
    val workspace = createRealSbtWorkspaceForBspOpen()
    val importEvents = observeBspImportEvents(workspace.root)

    // Verify the project has the prerequisites needed for the user-facing open flow.
    assertTrue(
      "A JDK should be available for BspOpenProjectProvider to generate the sbt BSP connection file",
      BspJdkUtil.findOrCreateBestJdkForProject(project).isDefined
    )

    // Exercise closing the IDE project while BSP import is still initializing.
    val (_, loggedExecutionErrors) = interceptBspExecutionErrors {
      runCloseDuringImportScenario(project, workspace, importEvents)
    }

    // Verify the close is treated as cancellation, not as an import execution failure.
    assertProjectCloseCanceledImport(project, importEvents, loggedExecutionErrors)
  }

  def testFinalImportCallbackSkipsProjectDataImportWhenProjectAlreadyDisposed(): Unit = {
    // Prepare a resolved BSP model and a test double for the final project-data import boundary.
    val project = getProject
    val workspace = createTemporaryWorkspace("disposedBspImport")
    val settings = new BspProjectSettings()
    settings.setExternalProjectPath(workspace.toString)

    val externalProject = createResolvedBspProjectData(workspace)
    val originalDataManager = ProjectDataManager.getInstance()
    val trackingDataManager = new TrackingProjectDataManager(originalDataManager)
    ServiceContainerUtil.replaceService(
      ApplicationManager.getApplication,
      classOf[ProjectDataManager],
      trackingDataManager,
      getTestRootDisposable
    )

    // Exercise the final import callback after the IDE project has already been closed.
    val provider = new BspOpenProjectProvider()
    val (_, loggedImportErrors) = CollectingLoggedMessagesProcessor.collectMatchingErrors(isDisposedProjectImportDataError) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      new provider.FinalImportCallback(project, settings).onSuccess(externalProject)
    }

    // Verify the callback exits without importing data or logging disposal-related errors.
    assertTrue("Project should be disposed before the callback receives resolved project data", project.isDisposed)
    assertFalse(
      "FinalImportCallback should not call ProjectDataManager.importData after the IDE project is disposed",
      trackingDataManager.importDataCalled.get()
    )
    assertTrue(
      s"Disposed project callback should not log importData disposal/control-flow errors: ${renderLoggedErrors(loggedImportErrors)}",
      loggedImportErrors.isEmpty
    )
  }

  private def runCloseDuringImportScenario(
    project: Project,
    workspace: BlockingSbtImportWorkspaceMarkerFiles,
    importEvents: BspImportEvents
  ): Unit =
    try {
      val openFuture = startUserBspOpenFlow(workspace.root, project)

      waitForConnectionFileGeneratedByProvider(workspace, importEvents)
      waitForFastSbtLoadDuringConnectionFileGeneration(workspace, importEvents)
      assertSkippedSbtLoadCount(workspace, expected = 1, "during BSP connection-file generation")

      waitForActualImportSbtLoadToBlock(workspace, importEvents)
      assertTotalSbtLoadCount(workspace, expected = 2, "after actual BSP import reached build loading")

      importEvents.startObservingActualImport()
      closeProjectWhileSbtLoadIsBlocked(project, workspace)

      waitForImportCancellation(importEvents)
      PlatformTestUtil.waitForFuture(openFuture, 20_000)
    } finally {
      workspace.releaseSbtLoadIfNeeded()
    }

  /**
   * Catches only the log symptom from SCL-24439 while preserving normal test-framework behavior for unrelated errors.
   *
   * Before the fix, closing a project during BSP session initialization could be routed through the generic "Problem executing BSP job" branch.
   * That branch logs an execution error and turns a user-initiated close into a noisy import failure.
   *
   * The test still lets every other logged error use the default processor.
   * So other exceptions withll be thrown and the test will fail.
   * This handling is needed just for a more trageted assertions.
   */
  private def interceptBspExecutionErrors[T](
    body: => T
  ): (T, Seq[CollectingLoggedMessagesProcessor.LoggedError]) =
    CollectingLoggedMessagesProcessor.collectMatchingErrors(_.allPartsConcatenatedText.contains("Problem executing BSP job")) {
      body
    }

  /**
   * Copies the real sbt workspace used by the close-during-import scenario.
   *
   * What: the fixture under `testdata/sbt/projects/bspCloseDuringImport` intentionally does not contain `.bsp/sbt.json`.
   * This method copies it to a temporary directory, refreshes VFS, verifies that the production provider sees it as an
   * sbt setup, and writes the skip marker consumed by the first sbt load.
   *
   * Why this method: the test should exercise the same BSP connection-file generation that a user-triggered project
   * open uses, then block only the later import load. A prebuilt fake workspace or a handwritten connection file would
   * skip the boundary where this race happens in practice.
   */
  private def createRealSbtWorkspaceForBspOpen(): BlockingSbtImportWorkspaceMarkerFiles = {
    val workspace = copyTestDataProjectToTempDir(TestDataProjectPath)

    val bspConnectionFile = workspace / ".bsp" / "sbt.json"
    assertFalse(
      "The fixture must not predefine a BSP connection file; the provider should generate it",
      Files.exists(bspConnectionFile)
    )
    assertTrue(
      "The copied fixture should be detected as an sbt setup so BspOpenProjectProvider can generate the BSP connection file",
      bspConfigSteps.workspaceSetupChoices(workspace).contains(bspConfigSteps.SbtSetup)
    )

    val result = BlockingSbtImportWorkspaceMarkerFiles.fromRoot(workspace)
    result.skipNextSbtLoad()
    result
  }

  private def copyTestDataProjectToTempDir(testDataProjectPath: Path): Path = {
    assertTrue(s"Missing testdata sbt BSP workspace: $testDataProjectPath", Files.isDirectory(testDataProjectPath))

    val workspace = createTemporaryWorkspace(testDataProjectPath.getFileName.toString)
    NioFiles.copyRecursively(testDataProjectPath, workspace)
    refreshCopiedWorkspace(workspace)
    workspace
  }

  private def createTemporaryWorkspace(name: String): Path = {
    val workspace = createTempDirectory().toPath.resolve(name)
    Files.createDirectories(workspace)
    workspace
  }

  private def refreshCopiedWorkspace(workspace: Path): Unit = {
    val workspaceFile = refreshAndFind(workspace)
    VfsUtil.markDirtyAndRefresh(false, true, true, workspaceFile)
    assertNotNull(
      s"Could not refresh copied build definition ${workspace.resolve("build.sbt")}",
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(workspace.resolve("build.sbt"))
    )
  }

  private def refreshAndFind(path: Path): VirtualFile = {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    assertNotNull(s"Could not refresh $path", file)
    file
  }

  private def observeBspImportEvents(workspace: Path): BspImportEvents = {
    val events = new BspImportEvents(workspace)
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(events, getTestRootDisposable)
    events
  }

  private def startUserBspOpenFlow(workspace: Path, project: Project): CompletableFuture[?] = {
    val workspaceFile = refreshAndFind(workspace)
    CompletableFuture.runAsync(
      { () => new BspOpenProjectProvider().doLinkProject(workspaceFile, project) },
      AppExecutorUtil.getAppExecutorService
    )
  }

  private def waitForConnectionFileGeneratedByProvider(
    workspace: BlockingSbtImportWorkspaceMarkerFiles,
    importEvents: BspImportEvents
  ): Unit =
    waitForWorkspaceCondition(
      workspace,
      importEvents,
      "BSP connection file was not generated",
      timeout = 60.seconds
    ) { _.hasGeneratedConnectionFile }

  private def waitForFastSbtLoadDuringConnectionFileGeneration(
    workspace: BlockingSbtImportWorkspaceMarkerFiles,
    importEvents: BspImportEvents
  ): Unit =
    waitForWorkspaceCondition(
      workspace,
      importEvents,
      "sbt BSP config generation did not consume the skip-load marker"
    ) { _.hasSkippedConnectionFileGenerationLoad }

  private def waitForActualImportSbtLoadToBlock(
    workspace: BlockingSbtImportWorkspaceMarkerFiles,
    importEvents: BspImportEvents
  ): Unit =
    waitForWorkspaceCondition(
      workspace,
      importEvents,
      "sbt BSP import did not reach build loading"
    ) { _.hasReachedBlockingImportLoad }

  private def waitForWorkspaceCondition(
    workspace: BlockingSbtImportWorkspaceMarkerFiles,
    importEvents: BspImportEvents,
    failurePrefix: String,
    timeout: scala.concurrent.duration.FiniteDuration = 20.seconds
  )(condition: BlockingSbtImportWorkspaceMarkerFiles => Boolean): Unit =
    AwaitTestUtils.waitForConditionOrFail(
      timeout,
      s"$failurePrefix for ${workspace.root}.\n${renderImportDiagnostics(workspace, importEvents)}",
    ) { () => condition(workspace) }

  private def assertTotalSbtLoadCount(workspace: BlockingSbtImportWorkspaceMarkerFiles, expected: Int, stage: String): Unit =
    assertEquals(
      s"Unexpected sbt build load count $stage for ${workspace.root}. ${workspace.markerDiagnostics}",
      expected,
      workspace.totalSbtLoadCount
    )

  private def assertSkippedSbtLoadCount(workspace: BlockingSbtImportWorkspaceMarkerFiles, expected: Int, stage: String): Unit =
    assertEquals(
      s"Unexpected skipped sbt build load count $stage for ${workspace.root}. ${workspace.markerDiagnostics}",
      Some(expected),
      workspace.skippedSbtLoadCount
    )

  private def closeProjectWhileSbtLoadIsBlocked(project: Project, workspace: BlockingSbtImportWorkspaceMarkerFiles): Unit = {
    val projectClosingObserved = new AtomicBoolean(false)
    ApplicationManager.getApplication.getMessageBus.connect(getTestRootDisposable).subscribe(
      ProjectManager.TOPIC,
      new ProjectManagerListener {
        override def projectClosing(closingProject: Project): Unit =
          if (closingProject == project) {
            projectClosingObserved.set(true)
          }
      }
    )

    val closeFuture = CompletableFuture.runAsync(
      { () =>
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      },
      AppExecutorUtil.getAppExecutorService
    )

    AwaitTestUtils.waitForConditionOrFail(5.seconds, "Project close lifecycle was not started") { () => projectClosingObserved.get() }
    workspace.releaseSbtLoad("project close lifecycle started")
    PlatformTestUtil.waitForFuture(closeFuture, 20_000)
  }

  private def waitForImportCancellation(events: BspImportEvents): Unit =
    AwaitTestUtils.waitForConditionOrFail(
      20.seconds,
      "BSP import was not reported as canceled after project close",
    ) { () => events.canceled.get() || events.failed.get() }

  private def assertProjectCloseCanceledImport(
    project: Project,
    events: BspImportEvents,
    loggedExecutionErrors: Seq[CollectingLoggedMessagesProcessor.LoggedError]
  ): Unit = {
    assertTrue("Project should be disposed after close", project.isDisposed)
    assertTrue("BSP import should be canceled when the project is closed", events.canceled.get())
    assertFalse("BSP import should not fail when the project is closed", events.failed.get())
    assertTrue(
      s"BSP cancellation should not be logged as an execution error: ${renderLoggedErrors(loggedExecutionErrors)}",
      loggedExecutionErrors.isEmpty
    )
  }

  private def renderLoggedErrors(loggedErrors: Seq[CollectingLoggedMessagesProcessor.LoggedError]): String =
    loggedErrors.map(error => s"${error.category}: ${error.allPartsConcatenatedText}").mkString("; ")

  private def renderImportDiagnostics(workspace: BlockingSbtImportWorkspaceMarkerFiles, events: BspImportEvents): String =
    s"${workspace.markerDiagnostics}\nBSP output:\n${events.output.asScala.mkString}"

  private def createResolvedBspProjectData(workspace: Path): DataNode[ProjectData] = {
    val projectData = new ProjectData(BSP.ProjectSystemId, "disposed-callback", workspace.toString, workspace.toString)
    new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)
  }

  private def isDisposedProjectImportDataError(error: CollectingLoggedMessagesProcessor.LoggedError): Boolean = {
    val text = error.allPartsConcatenatedText
    text.contains("ContainerDisposedException") || text.contains("should never be logged")
  }

  /**
   * Marker-file view of the copied sbt fixture.
   *
   * What: the test observes files created by `build.sbt` instead of parsing sbt output. That keeps synchronization tied
   * to explicit fixture events: connection-file generation load skipped, actual import load reached, and import load
   * released after project close.
   *
   * Why private: the marker contract is tailored to one fixture and one regression. Keeping it here makes future edits
   * read next to the test scenario and avoids turning a narrow race harness into a broad BSP test API too early.
   */
  private final case class BlockingSbtImportWorkspaceMarkerFiles(
    root: Path,
    connectionFile: Path,
    skipNextLoadMarker: Path,
    skippedLoadCountFile: Path,
    blockingLoadMarker: Path,
    totalLoadCountFile: Path,
    releaseLoadMarker: Path
  ) {
    def skipNextSbtLoad(): Unit =
      Files.writeString(skipNextLoadMarker, "skip BSP connection-file generation load")

    def hasGeneratedConnectionFile: Boolean =
      Files.exists(connectionFile)

    def hasSkippedConnectionFileGenerationLoad: Boolean =
      Files.exists(skippedLoadCountFile)

    def hasReachedBlockingImportLoad: Boolean =
      Files.exists(blockingLoadMarker)

    def totalSbtLoadCount: Int =
      readInt(totalLoadCountFile).getOrElse(0)

    def skippedSbtLoadCount: Option[Int] =
      readInt(skippedLoadCountFile)

    def markerDiagnostics: String =
      s"sbt load count: $totalSbtLoadCount; skipped load count: ${skippedSbtLoadCount.getOrElse("<missing>")}; " +
        s"skip marker exists: ${Files.exists(skipNextLoadMarker)}; " +
        s"skip consumed marker exists: ${Files.exists(skippedLoadCountFile)}; " +
        s"load marker exists: ${Files.exists(blockingLoadMarker)}; " +
        s"release marker exists: ${Files.exists(releaseLoadMarker)}"

    def releaseSbtLoad(reason: String): Unit =
      Files.writeString(releaseLoadMarker, reason)

    def releaseSbtLoadIfNeeded(): Unit =
      if (!Files.exists(releaseLoadMarker)) {
        releaseSbtLoad("cleanup")
      }

    private def readInt(file: Path): Option[Int] =
      if (Files.exists(file)) {
        val text = Files.readString(file).trim
        if (text.isEmpty) Some(0) else Some(text.toInt)
      } else
        None
  }

  private object BlockingSbtImportWorkspaceMarkerFiles {
    def fromRoot(workspace: Path): BlockingSbtImportWorkspaceMarkerFiles =
      BlockingSbtImportWorkspaceMarkerFiles(
        root = workspace,
        connectionFile = workspace / ".bsp" / "sbt.json",
        skipNextLoadMarker = workspace / "skip-sbt-build-load-once.marker",
        skippedLoadCountFile = workspace / "sbt-build-load-skip-consumed.txt",
        blockingLoadMarker = workspace / "sbt-build-load.marker",
        totalLoadCountFile = workspace / "sbt-build-load-count.txt",
        releaseLoadMarker = workspace / "release-sbt-build-load.marker"
      )
  }

  /**
   * Observes the external-system import result for the BSP workspace under test.
   *
   * What: the listener records whether the actual BSP `RESOLVE_PROJECT` task is canceled or failed, and keeps BSP
   * process output for diagnostics if sbt never reaches the build-load hold point.
   *
   * Why this API: the visible behavior of the race is reported through the external-system import pipeline. Observing
   * [[ExternalSystemProgressNotificationManager]] checks the same boundary the IDE uses for refresh
   * cancellation/failure while avoiding assertions against lower-level BSP session internals.
   *
   * Why `observeActualImport`: opening a BSP project first performs a preview refresh. The flag is enabled only after
   * sbt is blocked during build loading, so assertions apply to the close-during-import phase rather than to the
   * preliminary refresh.
   */
  private class BspImportEvents(workspace: Path) extends ExternalSystemTaskNotificationListener {
    val observeActualImport = new AtomicBoolean(false)
    val canceled = new AtomicBoolean(false)
    val failed = new AtomicBoolean(false)
    val output = new ConcurrentLinkedQueue[String]()

    def startObservingActualImport(): Unit =
      observeActualImport.set(true)

    override def onTaskOutput(id: ExternalSystemTaskId, text: String, outputType: ProcessOutputType): Unit =
      if (id.getProjectSystemId == BSP.ProjectSystemId) {
        output.add(text)
      }

    override def onCancel(projectPath: String, id: ExternalSystemTaskId): Unit =
      if (isObservedBspImport(projectPath, id)) {
        canceled.set(true)
      }

    override def onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception): Unit =
      if (isObservedBspImport(projectPath, id)) {
        failed.set(true)
      }

    private def isObservedBspImport(projectPath: String, id: ExternalSystemTaskId): Boolean =
      observeActualImport.get() &&
        id.getProjectSystemId == BSP.ProjectSystemId &&
        id.getType == ExternalSystemTaskType.RESOLVE_PROJECT &&
        projectPath == workspace.toString
  }

  /**
   * Test double for [[ProjectDataManager]] used by the disposed-project callback scenario.
   *
   * The production fix should make [[BspOpenProjectProvider.FinalImportCallback]] return before it asks the application
   * [[ProjectDataManager]] to import resolved project data after the IDE project has already been disposed. This wrapper
   * delegates unrelated API calls to the real service, but records and fails any `importData` call so the test checks
   * that exact boundary without relying on platform import internals to throw a particular disposal exception.
   */
  private class TrackingProjectDataManager(delegate: ProjectDataManager) extends ProjectDataManager {
    val importDataCalled = new AtomicBoolean(false)

    override def importData[T](node: DataNode[T], project: Project): Unit = {
      importDataCalled.set(true)
      throw new AssertionError("ProjectDataManager.importData should not be called for a disposed project")
    }

    override def importData[T](
      node: DataNode[T],
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ): Unit = {
      importDataCalled.set(true)
      throw new AssertionError("ProjectDataManager.importData should not be called for a disposed project")
    }

    override def findService(key: Key[?]): JList[ProjectDataService[?, ?]] =
      delegate.findService(key)

    override def findWorkspaceService(key: Key[?]): JList[WorkspaceDataService[?]] =
      delegate.findWorkspaceService(key)

    override def ensureTheDataIsReadyToUse(dataNode: DataNode[?]): Unit =
      delegate.ensureTheDataIsReadyToUse(dataNode)

    override def getExternalProjectData(
      project: Project,
      projectSystemId: ProjectSystemId,
      externalProjectPath: String
    ): ExternalProjectInfo =
      delegate.getExternalProjectData(project, projectSystemId, externalProjectPath)

    override def getExternalProjectsData(
      project: Project,
      projectSystemId: ProjectSystemId
    ): JCollection[ExternalProjectInfo] =
      delegate.getExternalProjectsData(project, projectSystemId)

    override def createModifiableModelsProvider(project: Project): IdeModifiableModelsProvider =
      delegate.createModifiableModelsProvider(project)
  }
}
