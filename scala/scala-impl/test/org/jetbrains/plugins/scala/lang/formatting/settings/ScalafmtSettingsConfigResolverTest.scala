package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileDocumentManagerListener}
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.extensions.inWriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil}
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService.ConfigResolveError.ConfigParseError
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalafmtSettingsConfigResolver.{Completion, Failure, LoadedConfig, Result, Version}
import org.junit.Assert._
import org.scalafmt.dynamic.ScalafmtVersion

import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

class ScalafmtSettingsConfigResolverTest extends HeavyPlatformTestCase {
  override def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    super.setUp()

    ScalaFmtForTestsSetupOps.ensureDownloaded(DefaultVersion, ScalafmtVersion(2, 7, 5))
  }

  def testExplicitVersion(): Unit = {
    val result = resolve("version = 2.7.5")
    assertEquals("An explicit version should resolve without the default marker",
      Right(Version(ScalafmtVersion(2, 7, 5), isDefault = false)), result)
  }

  def testMissingVersionUsesDefault(): Unit = {
    val result = resolve("maxColumn = 100")
    assertEquals("A configuration without a version should resolve the default formatter",
      Right(Version(DefaultVersion, isDefault = true)), result)
  }

  def testMalformedConfigurationHasNoRequestedVersion(): Unit = {
    val result = resolve("version = {")
    assertTrue(s"Malformed HOCON should produce a parse error before selecting a version: $result",
      result match {
        case Left(Failure(_: ConfigParseError, None)) => true
        case _ => false
      })
  }

  def testInvalidFormatterSettingRetainsRequestedVersion(): Unit = {
    val result = resolve("version = 2.7.5\nmaxColumn = invalid")
    assertTrue(s"A formatter configuration error should retain the selected version: $result",
      result match {
        case Left(Failure(_: ConfigParseError, Some(Version(version, false)))) =>
          version == ScalafmtVersion(2, 7, 5)
        case _ => false
      })
  }

  def testResolveFormatterForDefaultProject(): Unit = {
    val result = resolve("version = 2.7.5", defaultProject = true)
    assertEquals("Default-project Settings should resolve the configured formatter version",
      Right(Version(ScalafmtVersion(2, 7, 5), isDefault = false)), result)
  }

  def testMalformedConfigurationCompletesOffEdt(): Unit =
    checkCompletionOffEdt("version = {")

  def testCachedFormatterValidationCompletesOffEdt(): Unit =
    checkCompletionOffEdt("version = 2.7.5\nmaxColumn = invalid")

  def testUncachedDocumentLoadsOffEdt(): Unit = {
    val file = createConfigFile("version = 2.7.5")
    val readOffEdt = new AtomicBoolean(false)
    ApplicationManager.getApplication.getMessageBus.connect(getTestRootDisposable).subscribe(
      FileDocumentManagerListener.TOPIC,
      new FileDocumentManagerListener {
        override def fileContentLoaded(loaded: VirtualFile, document: Document): Unit = {
          if (loaded == file) readOffEdt.set(!ApplicationManager.getApplication.isDispatchThread)
        }
      }
    )
    assertNull("This test must exercise loading a document that is not cached",
      FileDocumentManager.getInstance().getCachedDocument(file))
    val result = resolveFile(file)
    assertTrue("Loading an uncached configuration document must run outside EDT", readOffEdt.get())
    assertTrue("The configuration should resolve successfully", result.isRight)
  }

  def testPreviewArrivesBeforeResolutionAndCanCancelRemainingWork(): Unit = {
    val file = createConfigFile("version = 2.7.5")
    val preview = new CompletableFuture[ScalafmtSettingsConfigResolver.LoadedConfig]()
    val release = new CountDownLatch(1)
    val finished = new CompletableFuture[Completion]()
    val indicator = EdtTestUtil.runInEdtAndGet(() => {
      ScalafmtSettingsConfigResolver.resolveConfigAsync(getProject, file.getPath, loaded => {
        preview.complete(loaded.get)
        assertTrue("The test must release the preview callback", release.await(10, TimeUnit.SECONDS))
      }, result => finished.complete(result))
    })
    try {
      awaitCompletion(preview)
      assertEquals("The preview should contain the file before formatter resolution finishes",
        "version = 2.7.5", preview.get().originalText)
      assertFalse("Resolution must not finish before delivering the preview", finished.isDone)
      indicator.cancel()
    } finally release.countDown()
    // Wait for the worker itself, not just for the absence of a callback during an arbitrary delay.
    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.waitWithEventsDispatching("Cancelled resolution should stop", () => !indicator.isRunning, 10)
    }, false)
    awaitCompletion(finished)
    assertEquals("Cancellation after loading must deliver a terminal cancellation outcome", Completion.Cancelled, finished.get())
  }

  def testCancellationBeforeLoadingDeliversOneTerminalOutcome(): Unit = {
    val file = createConfigFile("version = 2.7.5")
    val loaded = new AtomicBoolean(false)
    val completed = new CompletableFuture[Completion]()
    val completionCount = new AtomicInteger()
    val indicator = EdtTestUtil.runInEdtAndGet(() => {
      // Hold write access only while starting and cancelling: the worker cannot enter its first read action.
      // Do not wait for background work until the write action has returned.
      inWriteAction {
        val progress = ScalafmtSettingsConfigResolver.resolveConfigAsync(getProject, file.getPath,
          _ => loaded.set(true), completion => {
            completionCount.incrementAndGet()
            completed.complete(completion)
          })
        progress.cancel()
        progress
      }
    })
    awaitCompletion(completed)
    assertTrue("The test must cancel the worker", indicator.isCanceled)
    assertFalse("Cancellation before loading must not deliver a preview", loaded.get())
    assertEquals("Cancellation before loading must report cancellation rather than a missing file",
      Completion.Cancelled, completed.get())
    assertEquals("A cancelled task must report exactly one terminal outcome", 1, completionCount.get())
  }

  def testUnexpectedPipelineFailureDeliversTerminalOutcome(): Unit = {
    val file = createConfigFile("version = 2.7.5")
    val failure = new IllegalStateException("Synthetic preview delivery failure")
    val completed = new CompletableFuture[Completion]()
    EdtTestUtil.runInEdtAndWait(() => {
      // Interrupt the real worker at the preview boundary to exercise Task's failure callback.
      ScalafmtSettingsConfigResolver.resolveConfigAsync(getProject, file.getPath,
        _ => throw failure, completion => completed.complete(completion))
    })
    awaitCompletion(completed)
    assertEquals("An unexpected pipeline failure must finish the request with its cause",
      Completion.Failed(failure), completed.get())
  }

  def testUnsavedRootAndIncludedDocumentsAreUsed(): Unit = {
    val file = createConfigFile("version = 2.7.5")
    val included = file.getParent.toNioPath.resolve("included.conf")
    Files.writeString(included, "version = 2.7.5")
    val includedFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(included)
    EdtTestUtil.runInEdtAndWait(() => {
      inWriteAction {
        FileDocumentManager.getInstance().getDocument(file).setText("include \"included.conf\"\nmaxColumn = 100")
        FileDocumentManager.getInstance().getDocument(includedFile).setText(s"version = $DefaultVersion")
      }
    })
    assertEquals("Version selection and validation must use unsaved root and include contents",
      Right(Version(DefaultVersion, isDefault = false)), resolveFile(file))
  }

  def testValidationUsesTheRootTextDeliveredToPreview(): Unit = {
    val file = createConfigFile("version = 2.7.5\nmaxColumn = 100")
    val result = resolveFile(file, onLoaded = loaded => {
      // Simulate another editor changing the source after the preview snapshot has been captured.
      // Waiting for EDT here also proves that the preview callback holds no background read lock.
      EdtTestUtil.runInEdtAndWait(() => {
        inWriteAction(loaded.get.document.setText(s"version = $DefaultVersion\nmaxColumn = invalid"))
      })
    })
    assertEquals("Version selection and validation must describe the root text delivered to the preview",
      Right(Version(ScalafmtVersion(2, 7, 5), isDefault = false)), result)
  }

  private def checkCompletionOffEdt(text: String): Unit = {
    val completedOffEdt = new AtomicBoolean(false)
    resolve(text, onCompleted = () => completedOffEdt.set(!ApplicationManager.getApplication.isDispatchThread))
    assertTrue("Configuration parsing and validation must complete on a background thread, even with cached formatters",
      completedOffEdt.get())
  }

  private def resolve(configText: String, defaultProject: Boolean = false, onCompleted: () => Unit = () => ()): Result = {
    resolveFile(createConfigFile(configText), defaultProject, onCompleted)
  }

  private def createConfigFile(configText: String): VirtualFile = EdtTestUtil.runInEdtAndGet(() => {
    // Real files exercise document-backed parsing, VFS refresh and formatter configuration loading together.
    val projectRoot = createTestProjectStructure()
    val path = Path.of(projectRoot.getPath).resolve(".scalafmt.conf")
    Files.writeString(path, configText)
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    assertNotNull("The configuration must be visible in VFS before resolution", file)
    file
  })

  private def resolveFile(
    configFile: VirtualFile,
    defaultProject: Boolean = false,
    onCompleted: () => Unit = () => (),
    onLoaded: Option[LoadedConfig] => Unit = _ => ()
  ): Result = {
    val completed = new CompletableFuture[Result]()
    EdtTestUtil.runInEdtAndWait(() => {
      val project = if (defaultProject) ProjectManager.getInstance().getDefaultProject else getProject
      ScalafmtSettingsConfigResolver.resolveConfigAsync(project, configFile.getPath, onLoaded, result => {
        onCompleted()
        result match {
          case Completion.Resolved(resolved) => completed.complete(resolved)
          case other => completed.completeExceptionally(new AssertionError(s"Expected configuration resolution, got $other"))
        }
      })
    }, false)
    awaitCompletion(completed)
    completed.get()
  }

  private def awaitCompletion(future: CompletableFuture[?]): Unit =
    EdtTestUtil.runInEdtAndWait(() => {
      // VFS refresh delivers events on EDT; keep dispatching without holding write intent.
      PlatformTestUtil.waitWithEventsDispatching("Configuration loading should complete", () => future.isDone, 10)
    }, false)
}
