package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil}
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.apache.ivy.core.settings.IvySettings
import org.jetbrains.plugins.scala.DependencyManagerBase.MavenResolver
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicServiceImpl.ProgressIndicatorDownloadListener
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtDynamicDownloader.{DownloadFailure, DownloadSuccess}
import org.junit.Assert._
import org.scalafmt.dynamic.ScalafmtVersion

import java.io.{ByteArrayOutputStream, IOException}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, CountDownLatch, ExecutionException, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.jar.JarOutputStream
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

/**
 * Exercises the real downloader, Ivy and progress cancellation against a local HTTP repository.
 * Each scenario has an empty cache; no public repository or preloaded formatter can satisfy it.
 * The tiny valid archives test transfer and retry, not formatter class loading or formatting.
 */
class ScalafmtDynamicDownloaderTest extends HeavyPlatformTestCase {
  override def runInDispatchThread(): Boolean = false

  def testHttpDownloadFailureCanBeRetried(): Unit = {
    val repository = new TestRepository
    try {
      val first = repository.startDownload()
      await(first.result, repository, "The HTTP failure should finish formatter downloading")
      assertTrue(s"HTTP failure must retain the requested formatter version: ${first.result.get()}",
        first.result.get() match {
          case Left(DownloadFailure(version, _)) => version == Version
          case _ => false
        })
      assertTrue("The failure must come from an actual artifact GET request", repository.artifactRequests.get() > 0)
      assertRetrySucceeds(repository)
    } finally repository.close()
  }

  def testCancellationDuringHttpTransferPropagatesAndCanBeRetried(): Unit = {
    val repository = new TestRepository
    repository.behavior = TransferBehavior.Block
    try {
      val first = repository.startDownload()
      await(repository.transferStarted, repository, "Ivy should start transferring an uncached formatter artifact")
      assertFalse("The transfer must still be blocked when the user cancels", first.result.isDone)
      assertFalse("The worker must not be cancelled before the transfer starts", first.indicator.isCanceled)
      first.indicator.cancel()

      // Keep the HTTP response blocked: cancellation must finish without waiting for the server.
      await(first.result, repository, "Cancellation should interrupt a blocked formatter transfer")
      val error = assertThrows("Cancellation must propagate instead of becoming a download failure",
        classOf[ExecutionException], () => first.result.get())
      assertTrue(s"The downloader must preserve platform cancellation: ${error.getCause}",
        error.getCause.isInstanceOf[ProcessCanceledException])
      repository.releaseTransfer.countDown()
      assertRetrySucceeds(repository)
    } finally repository.close()
  }

  def testClosingRepositoryCancelsAndAwaitsPendingDownload(): Unit = {
    val repository = new TestRepository
    repository.behavior = TransferBehavior.Block
    val first = try {
      val download = repository.startDownload()
      await(repository.transferStarted, repository, "The download must reach the blocked HTTP transfer before cleanup")
      assertFalse("Cleanup must run while the download is still pending", download.result.isDone)
      assertNotNull("The blocked download must have an Ivy worker", download.ivyThread.get())
      assertTrue("Cleanup must run while Ivy is still transferring the artifact", download.ivyThread.get().isAlive)
      download
    } finally repository.close()

    try {
      assertTrue("Repository cleanup must cancel its pending download", first.indicator.isCanceled)
      assertTrue("Repository cleanup must await the download worker before returning", first.result.isDone)
      assertFalse("Repository cleanup must also await Ivy's separate worker", first.ivyThread.get().isAlive)
      assertFalse("Repository cleanup must delete the cache after its workers finish", Files.exists(repository.cache))
    } finally {
      // Keep this regression safe when it is run against cleanup that does not own its workers yet.
      first.indicator.cancel()
      await(first.result, repository, "The cleanup regression must not leave a download worker running")
    }
  }

  private val Version = ScalafmtVersion(3, 11, 5)

  private case class Download(
    result: CompletableFuture[Either[DownloadFailure, DownloadSuccess]],
    indicator: ProgressIndicatorBase,
    ivyThread: AtomicReference[Thread]
  )

  private def assertRetrySucceeds(repository: TestRepository): Unit = {
    val requestsBefore = repository.artifactRequests.get()
    repository.behavior = TransferBehavior.Succeed
    val retry = repository.startDownload()
    await(retry.result, repository, "Retry should download artifacts using the same repository and cache")
    retry.result.get() match {
      case Right(DownloadSuccess(version, jars)) =>
        assertEquals("Retry must retain the requested formatter version", Version, version)
        assertFalse("Successful downloading must produce archive files", jars.isEmpty)
        jars.foreach { jar =>
          val path = Path.of(jar.toURI)
          assertTrue(s"Downloaded archive $jar must belong to this scenario's isolated cache", path.startsWith(repository.cache))
          assertArrayEquals(s"Downloaded archive $jar must contain the complete HTTP response",
            repository.archive, Files.readAllBytes(path))
        }
      case other => fail(s"Retry should succeed after interruption, got $other")
    }
    assertTrue("Retry must fetch artifacts rather than reuse a failed or partial download",
      repository.artifactRequests.get() > requestsBefore)
  }

  private def await(future: CompletableFuture[?], repository: TestRepository, message: String): Unit = {
    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.waitWithEventsDispatching(message, () => future.isDone || repository.failure.isDone, 15)
    }, false)
    if (repository.failure.isDone) throw new AssertionError("The local repository failed", repository.failure.get())
  }

  private enum TransferBehavior {
    case Fail, Block, Succeed
  }

  private class TestRepository extends AutoCloseable {
    private val downloads = ArrayBuffer.empty[Download]

    val ResolverName = "scalafmt-test-http"
    val cache: Path = Files.createTempDirectory("scalafmt-download-cache")
    val artifactRequests = new AtomicInteger()
    val transferStarted = new CompletableFuture[Unit]()
    val releaseTransfer = new CountDownLatch(1)
    val failure = new CompletableFuture[Throwable]()
    @volatile var behavior: TransferBehavior = TransferBehavior.Fail

    val archive: Array[Byte] = {
      val bytes = new ByteArrayOutputStream
      new JarOutputStream(bytes).close()
      bytes.toByteArray
    }

    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.setExecutor(executor)
    server.createContext("/", exchange => handle(exchange))
    server.start()
    val url: String = s"http://127.0.0.1:${server.getAddress.getPort}/"

    def startDownload(): Download = {
      // Override only repository/cache configuration. Dependency calculation, Ivy, HTTP and cancellation stay real.
      Registry.get("scala.dependency.manager.use.file.system.resolvers.only").setValue(false, getTestRootDisposable)
      val indicator = new ProgressIndicatorBase
      val ivyThread = new AtomicReference[Thread]()
      val downloader = new ScalafmtDynamicDownloader(
        Seq(MavenResolver(ResolverName, url)),
        new ProgressIndicatorDownloadListener(indicator)
      ) {
        override protected def customizeIvySettings(settings: IvySettings): Unit = {
          // DependencyManager initializes these settings on its dedicated Ivy resolution thread.
          ivyThread.set(Thread.currentThread())
          settings.setDefaultCache(cache.toFile)
          settings.setDefaultResolver(ResolverName)
        }
      }
      val result = CompletableFuture.supplyAsync(
        () => ProgressManager.getInstance().runProcess(new Computable[Either[DownloadFailure, DownloadSuccess]] {
          override def compute(): Either[DownloadFailure, DownloadSuccess] = downloader.download(Version)
        }, indicator),
        AppExecutorUtil.getAppExecutorService
      )
      val download = Download(result, indicator, ivyThread)
      downloads += download
      download
    }

    private def handle(exchange: HttpExchange): Unit = {
      val currentBehavior = behavior
      try {
        val path = exchange.getRequestURI.getPath
        if (path.endsWith(".pom")) {
          // Minimal metadata avoids transitive public dependencies while keeping real Maven/Ivy resolution.
          val segments = path.stripPrefix("/").split('/')
          val group = segments.dropRight(3).mkString(".")
          val artifact = segments(segments.length - 3)
          val version = segments(segments.length - 2)
          respond(exchange, s"""<project><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$artifact</artifactId><version>$version</version></project>""".getBytes(UTF_8))
        } else if (path.endsWith(".jar")) {
          if (exchange.getRequestMethod == "HEAD") respond(exchange, archive)
          else {
            artifactRequests.incrementAndGet()
            currentBehavior match {
              case TransferBehavior.Fail => exchange.sendResponseHeaders(503, -1)
              case TransferBehavior.Succeed => respond(exchange, archive)
              case TransferBehavior.Block =>
                exchange.sendResponseHeaders(200, archive.length)
                exchange.getResponseBody.write(archive, 0, 1)
                exchange.getResponseBody.flush()
                transferStarted.complete(())
                assertTrue("The test must release the blocked HTTP transfer", releaseTransfer.await(30, TimeUnit.SECONDS))
            }
          }
        } else exchange.sendResponseHeaders(404, -1)
      } catch {
        case _: IOException if currentBehavior == TransferBehavior.Block => // The cancelled client closes its connection.
        case NonFatal(error) => failure.complete(error)
      } finally exchange.close()
    }

    private def respond(exchange: HttpExchange, body: Array[Byte]): Unit = {
      if (exchange.getRequestMethod == "HEAD") {
        exchange.getResponseHeaders.set("Content-Length", body.length.toString)
        exchange.sendResponseHeaders(200, -1)
      } else {
        exchange.sendResponseHeaders(200, body.length)
        exchange.getResponseBody.write(body)
      }
    }

    private def awaitDownloads(message: String)(finished: Download => Boolean): Unit =
      EdtTestUtil.runInEdtAndWait(() => {
        PlatformTestUtil.waitWithEventsDispatching(message, () => downloads.forall(finished), 15)
      }, false)

    override def close(): Unit = {
      try {
        // Register Ivy's worker before cancellation can finish the caller's future without joining it.
        awaitDownloads("Downloads must finish or initialize Ivy before cleanup cancellation") { download =>
          download.result.isDone || download.ivyThread.get() != null
        }
      } finally {
        // Cancel the work itself: cancelling a CompletableFuture does not stop its supplier.
        downloads.iterator.filterNot(_.result.isDone).foreach(_.indicator.cancel())
        releaseTransfer.countDown()
        try {
          server.stop(0)
          awaitDownloads("Repository cleanup must wait for the download and Ivy workers to stop") { download =>
            download.result.isDone && Option(download.ivyThread.get()).forall(!_.isAlive)
          }
        } finally executor.close()
      }
      // Keep the cache if waiting times out: an active download may still be writing to it.
      assertTrue(s"Repository cleanup must delete its isolated cache $cache", FileUtil.delete(cache.toFile))
    }
  }
}
