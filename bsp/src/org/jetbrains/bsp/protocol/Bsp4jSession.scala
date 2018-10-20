package org.jetbrains.bsp.protocol

import java.io.{InputStream, OutputStream}
import java.util.concurrent.{CompletableFuture, LinkedBlockingQueue, TimeUnit, TimeoutException}

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.{CompileReport => _, _}
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.{Launcher, ResponseErrorException}
import org.jetbrains.bsp._
import org.jetbrains.bsp.protocol.Bsp4jNotifications._
import org.jetbrains.bsp.protocol.Bsp4jSession._
import org.jetbrains.bsp.protocol.BspCommunication._

import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.control.NonFatal

class Bsp4jSession(bspIn: InputStream,
                   bspOut: OutputStream,
                   initializeBuildParams: bsp4j.InitializeBuildParams,
                   cleanup: ()=>Unit
                  ) {

  private val logger = Logger.getInstance(classOf[BspCommunication])

  private val jobs = new LinkedBlockingQueue[SessionBspJob[_,_]]

  private var currentJob: SessionBspJob[_,_] = DummyJob

  private val serverConnection: ServerConnection = startServerConnection
  private val sessionInitialized = initializeSession
  private val sessionShutdown = Promise[Unit]

  private val queueProcessor = AppExecutorUtil.getAppScheduledExecutorService
      .scheduleWithFixedDelay(() => nextQueuedCommand, 10, 10, TimeUnit.MILLISECONDS)


  private def nextQueuedCommand= {
    val timeout = 1.second
    try {
      waitForSessionWithTimeout
      val currentIgnoringErrors = currentJob.future.recover {
        case NonFatal(_) => ()
      }(ExecutionContext.global)
      Await.result(currentIgnoringErrors, timeout) // will throw on job error

      val next = jobs.poll(timeout.toMillis, TimeUnit.MILLISECONDS)
      if (next != null) {
        currentJob = next
        currentJob.run(serverConnection.server)
      }
    } catch {
      case _: TimeoutException => // just carry on
      case error: BspConnectionError =>
        logger.warn("problem connecting to bsp server", error)
        shutdown(Some(error))
      case NonFatal(error) =>
        logger.error(error)
    }
  }

  private def startServerConnection: ServerConnection = {

    val localClient = new BspClient {
      override def onBuildShowMessage(params: ShowMessageParams): Unit = currentJob.notification(ShowMessage(params))
      override def onBuildLogMessage(params: LogMessageParams): Unit = currentJob.notification(LogMessage(params))
      override def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = currentJob.notification(PublishDiagnostics(params))
      override def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = () // TODO https://youtrack.jetbrains.com/issue/SCL-14475
      override def onBuildTargetCompileReport(params: bsp4j.CompileReport): Unit = currentJob.notification(CompileReport(params))
      override def buildRegisterFileWatcher(params: RegisterFileWatcherParams): CompletableFuture[RegisterFileWatcherResult] = null // TODO
      override def buildCancelFileWatcher(params: CancelFileWatcherParams): CompletableFuture[CancelFileWatcherResult] = null // TODO
    }

    val launcher = new Launcher.Builder[BspServer]()
      //      .traceMessages(new PrintWriter(System.out))
      .setRemoteInterface(classOf[BspServer])
      .setExecutorService(AppExecutorUtil.getAppExecutorService)
      .setInput(bspIn)
      .setOutput(bspOut)
      .setLocalService(localClient)
      .create()
    val listening = launcher.startListening()
    val bspServer = launcher.getRemoteProxy
    localClient.onConnectWithServer(bspServer)

    val cancelable = Cancelable { () =>
      Cancelable.cancelAll(
        List(
          Cancelable(() => cleanup()),
          Cancelable(() => bspIn.close()),
          Cancelable(() => bspOut.close()),
          Cancelable(() => listening.cancel(true))
          // TODO stop bsp server process
        )
      )
    }

    ServerConnection(bspServer, cancelable)
  }

  private def initializeSession: CompletableFuture[InitializeBuildResult] = {
    val bsp = serverConnection.server
    bsp.buildInitialize(initializeBuildParams)
      .thenApply[InitializeBuildResult] { result =>
        bsp.onBuildInitialized()
        result
      }
      .exceptionally {
        case responseError: ResponseErrorException => throw BspConnectionError(responseError.getMessage, responseError)
      }
  }

  private def waitForSessionWithTimeout: InitializeBuildResult = try {
    sessionInitialized.get(5, TimeUnit.SECONDS)
  } catch {
    case to: TimeoutException => throw BspConnectionError("bsp server is not responding", to)
  }



  /** Run a task with client in this session.
    * Notifications during run of this task are passed to the aggregator. This can also be used for plain callbacks.
    */
  def run[T, A](task: Bsp4jSessionTask[T], default: A, aggregator: NotificationAggregator4j[A]): BspJob[(T,A)] = {
    val job = new Bsp4jJob(task, default, aggregator)
    jobs.put(job)
    job
  }

  def isAlive: Boolean = ! sessionShutdown.isCompleted

  def shutdown(error: Option[BspError] = None): CompletableFuture[Unit] = {
    def whenDone: CompletableFuture[Unit] = {
      serverConnection.server.buildShutdown()
        .thenApply[Unit](_=>())
        .whenComplete {(_, error) =>
          error match {
            case err: ResponseErrorException =>
              val msg = err.getMessage
              val errorObject = err.getResponseError
              BSP.balloonNotification.createNotification(msg, NotificationType.ERROR)
              val fullMessage = s"$msg (code ${errorObject.getCode}). Data: ${errorObject.getData}"
              logger.error(fullMessage)
          }

          serverConnection.cancelable.cancel()
        }

      // TODO timeout shutdown
      // TODO check process state, hard-kill bsp process if shutdown was not orderly
    }

    error match {
      case None =>
        sessionShutdown.success(())
        currentJob.cancel()
        jobs.forEach(_.cancel())
      case Some(err) =>
        sessionShutdown.failure(err)
        currentJob.cancelWithError(err)
        jobs.forEach(_.cancelWithError(err))
    }
    queueProcessor.cancel(false)
    sessionInitialized.cancel(false)
    whenDone
  }
}

object Bsp4jSession {

  trait BspServer extends BuildServer with ScalaBuildServer
  trait BspClient extends BuildClient


  private abstract class SessionBspJob[T,A] extends BspJob[(T,A)] {
    private[Bsp4jSession] def notification(bspNotification: Bsp4jNotification): Unit
    private[Bsp4jSession] def run(bspServer: BspServer): CompletableFuture[(T, A)]
    private[Bsp4jSession] def cancelWithError(error: BspError)
  }

  private class Bsp4jJob[T,A](task: Bsp4jSessionTask[T], default: A, aggregator: NotificationAggregator4j[A]) extends SessionBspJob[T,A] {

    private val promise = Promise[(T,A)]
    private var a: A = default

    private var runningTask: Option[CompletableFuture[(T,A)]] = None

    override private[Bsp4jSession] def notification(bspNotification: Bsp4jNotification): Unit =
      a = aggregator(a, bspNotification)

    private def doRun(bspServer: BspServer): CompletableFuture[(T,A)] = {
      task(bspServer)
        .thenApply[(T,A)]((t: T) => (t,a))
        .whenComplete((result: (T,A), u: Throwable) => {
          if (u != null) u match {
            case _: CancellationException => promise.failure(BspTaskCancelled)
            case NonFatal(err) => promise.failure(err)
            case fatal => throw fatal
          } else {
            promise.success(result)
          }
        })
    }

    private[Bsp4jSession] def run(bspServer: BspServer): CompletableFuture[(T, A)] = runningTask.synchronized {
      runningTask match {
        case Some(running) =>
          running
        case None =>
          val running = doRun(bspServer)
          runningTask = Some(running)
          running
      }
    }

    override def future: Future[(T, A)] = promise.future

    override def cancel() : Unit =
      cancelWithError(BspTaskCancelled)

    override def cancelWithError(error: BspError): Unit = runningTask.synchronized {
      runningTask match {
        case Some(toCancel) =>
          toCancel.completeExceptionally(error)
        case None =>
          val errorFuture = new CompletableFuture[(T,A)]
          errorFuture.completeExceptionally(error)
          runningTask = Some(errorFuture)
          promise.failure(error)
      }
    }
  }

  // TODO barebones handling of logMessage/showMessage?
  private object DummyJob extends SessionBspJob[Unit,Unit] {
    override private[Bsp4jSession] def notification(bspNotification: Bsp4jNotification): Unit = ()
    override private[Bsp4jSession] def run(bspServer: BspServer): CompletableFuture[(Unit, Unit)] = CompletableFuture.completedFuture(((),()))
    override private[Bsp4jSession] def cancelWithError(error: BspError): Unit = ()
    override def future: Future[(Unit,Unit)] = Future.successful(((),()))
    override def cancel(): Unit = ()
  }

  private case class ServerConnection(server: BspServer, cancelable: Cancelable)

  private trait Cancelable {
    def cancel(): Unit
  }

  private class OpenCancelable extends Cancelable {
    private val toCancel = ListBuffer.empty[Cancelable]
    def add(cancelable: Cancelable): Unit = toCancel += cancelable
    override def cancel(): Unit = Cancelable.cancelAll(toCancel)
  }
  private object Cancelable {
    def apply(fn: () => Unit): Cancelable = () => fn()
    val empty: Cancelable = Cancelable(() => ())
    def cancelAll(iterable: Iterable[Cancelable]): Unit = {
      var errors = ListBuffer.empty[Throwable]
      iterable.foreach { cancelable =>
        try cancelable.cancel()
        catch { case NonFatal(ex) => errors += ex }
      }
      errors.toList match {
        case head :: tail =>
          tail.foreach { e =>
            if (e ne head) {head.addSuppressed(e)}
          }
          throw head
        case _ =>
      }
    }
  }

}
