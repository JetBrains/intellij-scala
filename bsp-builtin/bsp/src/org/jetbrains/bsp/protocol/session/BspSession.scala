package org.jetbrains.bsp.protocol.session

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.{BuildServerCapabilities, InitializeBuildResult}
import com.intellij.execution.process.OSProcessUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.{Launcher, ResponseErrorException}
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bsp.*
import org.jetbrains.bsp.protocol.BspJob
import org.jetbrains.bsp.protocol.BspNotifications.*
import org.jetbrains.bsp.protocol.session.BspSession.*
import org.jetbrains.bsp.protocol.session.GenericConnector.RemoteProcessPid
import org.jetbrains.bsp.protocol.session.jobs.BspSessionJob
import org.jetbrains.plugins.scala.extensions.PathExt

import java.io.{InputStream, OutputStream, PrintWriter, Writer}
import java.lang.reflect.{InvocationHandler, Method}
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.{Callable, CompletableFuture, LinkedBlockingQueue, TimeUnit}
import scala.annotation.tailrec
import scala.concurrent.*
import scala.concurrent.duration.*
import scala.io.Source
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class BspSession private(bspPID: Long,
                         bspIn: InputStream,
                         bspErr: InputStream,
                         bspOut: OutputStream,
                         initializeBuildParams: bsp4j.InitializeBuildParams,
                         cleanup: ()=>Unit,
                         notificationCallbacks: List[NotificationCallback],
                         initialJob: BspSessionJob[?, ?],
                         traceLogPredicate: () => Boolean
                        ) {

  private val logger = Logger.getInstance(classOf[BspSession])

  {
    logger.debug(s"new BspSession(bspPID: $bspPID)")
  }

  private val jobs = new LinkedBlockingQueue[BspSessionJob[?,?]]

  private var currentJob: BspSessionJob[?,?] = initialJob

  private var lastProcessOutput: Long = System.currentTimeMillis()
  private var lastActivity: Long = lastProcessOutput

  private val serverConnection: ServerConnection = startServerConnection
  private val sessionInitialized: CompletableFuture[InitializeBuildResult] = initializeSession
  private val sessionShutdown: Promise[Unit] = Promise[Unit]()

  private val queuePause = 10.millis
  private val queueTimeout = 1.second
  private val sessionTimeout: FiniteDuration =
    if (ApplicationManager.getApplication.isUnitTestMode && SystemInfo.isWindows) 5.minutes
    else 20.seconds

  private val queueProcessor = AppExecutorUtil.getAppScheduledExecutorService
    .scheduleWithFixedDelay(() => nextQueuedCommand(), queuePause.toMillis, queuePause.toMillis, TimeUnit.MILLISECONDS)

  @VisibleForTesting
  private[protocol] def notifications(notification: BspNotification): Unit =
    notificationCallbacks.foreach(_.apply(notification))
  
  private def nextQueuedCommand(): Unit = {
    val initialisationResult = waitForSessionInitialization(sessionTimeout)
    initialisationResult match {
      case Success(initResult) =>
        runCurrentJobAndPollNext(initResult)
      case Failure(BspTaskCancelled) =>
        shutdown()
      case Failure(error: BspConnectionError) =>
        logger.warn(error)
        shutdown(Some(error))
      case Failure(error) =>
        val msg = BspBundle.message("bsp.protocol.problem.connecting.to.bsp.server", error.getMessage)
        val bspError = BspException(msg, error)
        logger.warn(bspError)
        if (this.isAlive) {
          //the session could be explicitly shutdown already (e.g. via "Stop" action in BSP Connection widget)
          shutdown(Some(bspError))
        }
    }
  }

  private def runCurrentJobAndPollNext(initResult: InitializeBuildResult): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    try {
      val buildServerInfo = BuildServerInfo(initResult.getDisplayName, initResult.getCapabilities)
      currentJob.run(serverConnection.server, buildServerInfo) // in case not yet running
      val currentIgnoringErrors = currentJob.future
        .recover { case NonFatal(_) => () }
        .andThen { case _ => lastActivity = System.currentTimeMillis()}

      Await.result(currentIgnoringErrors, queueTimeout) // will throw on job error

      val next = jobs.poll(queueTimeout.toMillis, TimeUnit.MILLISECONDS)
      if (next != null) {
        currentJob = next
        currentJob.run(serverConnection.server, buildServerInfo)
      }
    } catch {
      case _: TimeoutException => // just carry on
      case NonFatal(error) =>
        val bspError = BspException(BspBundle.message("bsp.protocol.problem.executing.bsp.job"), error)
        logger.error(bspError)
        currentJob.cancelWithError(bspError)
    }
  }

  private def lazyFileCreateWriter(file: Path): Writer = new Writer() {

    lazy val writer: Writer = {
      logger.debug(s"Writing BSP trace log to file ${file.getFileName.toString}")
      import StandardOpenOption.*
      Files.newBufferedWriter(file, Charset.defaultCharset(), WRITE, CREATE, APPEND)
    }


    override def write(cbuf: Array[Char], off: Int, len: Int): Unit = {
      writer.write(cbuf, off, len)
    }

    override def flush(): Unit = {
      writer.flush()
    }

    override def close(): Unit = {
      writer.close()
    }
  }

  private def bspTraceLogger: Option[PrintWriter] = {
    if (traceLogPredicate()) {
      val logfile = sys.env.get("BSP_TRACE_PATH")
        .orElse(sys.props.get("BSP_TRACE_PATH"))
        .map(Path.of(_))
        .getOrElse({
          val dirs = Paths.get(PathManager.getLogPath, "bsp")
          Files.createDirectories(dirs)
          val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
            .format(LocalDateTime.now())
          dirs / s"bsp-protocol-trace-$stamp.log"
        })
      Some(new PrintWriter(lazyFileCreateWriter(logfile)))
    } else {
      None
    }
  }


  private def startServerConnection: ServerConnection = {

    val localClient = new BspSessionClient

    val launcher = new Launcher.Builder[BspServer]()
      .setRemoteInterface(classOf[BspServer])
      .setExecutorService(AppExecutorUtil.getAppExecutorService)
      .setInput(bspIn)
      .setOutput(bspOut)
      .setLocalService(localClient)
      .traceMessages(bspTraceLogger.orNull)
      .create()
    val listening = launcher.startListening()
    val bspServer = cancellationSafeBspServer(launcher.getRemoteProxy)
    localClient.onConnectWithServer(bspServer)

    val messageHandler = new BspProcessMessageHandler(bspErr)
    val messageHandlerRunning = AppExecutorUtil.getAppExecutorService.submit(messageHandler)

    val cancelable = Cancelable { () =>
      Cancelable.cancelAll(
        List(
          Cancelable(() => bspIn.close()),
          Cancelable(() => bspErr.close()),
          Cancelable(() => bspOut.close()),
          Cancelable(() => listening.cancel(true)),
          Cancelable(() => messageHandlerRunning.cancel(true)),
          Cancelable(() => cleanup())
        )
      )
    }

    ServerConnection(bspServer, cancelable, listening)
  }

  /**
   * Some BSP endpoints return `CompletableFuture`s that represent BSP jobs that are
   * currently running on BSP server. In order to cancel these, jobs, the `cancel`
   * method of this future should be called. Unfortunately, the original futures that come
   * from lsp4j does not support transformations well - after calling `thenApply`, the `cancel`
   * method of the new future does not stop the job. This method returns a BspServer's proxy that
   * return fixed CancellableFuture's
   *
   * @param bspServer original bspServer with endpoints returning regular `CompletableFuture`s
   * @return proxy of bspServer with endpoints returning fixed `CompletableFuture`s
   */
  private def cancellationSafeBspServer(bspServer: BspServer): BspServer = {
    val invocationHandler = new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
        val resultFromBsp = method.invoke(bspServer, args*)
        // Some BSP endpoints return CompletableFutures, but other return void
        resultFromBsp match {
          case future: CompletableFuture[_] => CancellableFuture.from(future)
          case x => x
        }
      }
    }

    val safeBspServer = java.lang.reflect.Proxy
      .newProxyInstance(invocationHandler.getClass.getClassLoader,
        Array(classOf[BspServer]),
        invocationHandler
      ).asInstanceOf[BspServer]

    safeBspServer
  }

  private def initializeSession: CompletableFuture[bsp4j.InitializeBuildResult] = {
    val bspServer = serverConnection.server
    bspServer.buildInitialize(initializeBuildParams)
      .thenApply[bsp4j.InitializeBuildResult] { result =>
        bspServer.onBuildInitialized()
        result
      }
      .exceptionally {
        case NonFatal(error) =>
          throw BspSessionInitializationErrorNormalizer.fromBuildInitializeFailure(error)
      }
  }

  @tailrec
  private def waitForSessionInitialization(timeout: Duration): Try[bsp4j.InitializeBuildResult] = try {
    Success(sessionInitialized.get(timeout.toMillis, TimeUnit.MILLISECONDS))
  } catch {
    case to: TimeoutException =>
      val now = System.currentTimeMillis()
      val waited = now - lastProcessOutput
      if (waited > timeout.toMillis)
        Failure(BspConnectionError(BspBundle.message("bsp.protocol.bsp.server.is.not.responding"), to))
      else
        waitForSessionInitialization(timeout)
    case NonFatal(error) =>
      Failure(BspSessionInitializationErrorNormalizer.fromSessionInitializationAwaitFailure(error))
  }

  /** Run a task with client in this session.
    * Notifications during run of this task are passed to the aggregator. This can also be used for plain callbacks.
    */
  private[protocol] def run[T, A](job: BspSessionJob[T,A]): BspJob[(T,A)] = {
    val resultJob = if (isAlive) {
      job
    } else {
      new FailedBspSessionJob[T, A](BspException(BspBundle.message("bsp.protocol.session.is.not.available"), deathReason.orNull))
    }
    jobs.put(resultJob)
    resultJob
  }

  private[protocol] def isAlive: Boolean = {
    !serverConnection.listening.isDone &&
      !sessionShutdown.isCompleted &&
      !queueProcessor.isDone
  }

  private def deathReason = {
    val sessionError = sessionShutdown.future.value.flatMap {
      case Success(_) => None
      case Failure(exception) => Some(exception)
    }
    val queueError = Try(queueProcessor.get()) match {
      case Success(_) => None
      case Failure(exception) => Some(exception)
    }

    sessionError.orElse(queueError)
  }

  private[protocol] def shutdown(error: Option[BspError] = None): Future[Unit] = {
    import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext

    def whenDone: CompletableFuture[Unit] = {
      // Query all child processes of the BSP server recursively.
      // The sbt BSP server spawns a separate sbt process which needs to be manually shut down. #SCL-19315
      // These remaining child processes are manually terminated after the server has been stopped.
      // #descendants method is not available in the remote context, and it's unnecessary there to kill the child sbt process.
      // See org.jetbrains.bsp.protocol.session.GenericConnector.prepareBspSession for explanation.
      val descendants =
        if (bspPID != RemoteProcessPid) ProcessHandle.of(bspPID).stream().flatMap(_.descendants()).toList
        else java.util.List.of()

      serverConnection.server.buildShutdown()
        .thenApply[Unit](_=> serverConnection.server.onBuildExit())
        .thenApply[Unit](_ => descendants.stream().filter(_.isAlive).forEach(BspSession.terminateProcessGracefully(_, sessionTimeout)))
        .whenComplete {(_, error) =>
          error match {
            case err: ResponseErrorException =>
              val msg = err.getMessage
              val errorObject = err.getResponseError
              BSP.NotificationGroup.createNotification(msg, NotificationType.ERROR)
              val fullMessage = s"$msg (code ${errorObject.getCode}). Data: ${errorObject.getData}"
              logger.error(fullMessage)
          }
        }
    }

    error match {
      case None =>
        sessionShutdown.trySuccess(())
        currentJob.cancel()
        jobs.forEach(_.cancel())
      case Some(err) =>
        sessionShutdown.tryFailure(err)
        currentJob.cancelWithError(err)
        jobs.forEach(_.cancelWithError(err))
    }
    queueProcessor.cancel(false)
    sessionInitialized.cancel(false)

    // ensure connection-related stuff is canceled after a timeout
    Future(whenDone.get(sessionTimeout.toMillis, TimeUnit.MILLISECONDS))
      .andThen { case _ => serverConnection.cancelable.cancel() }
  }

  private[protocol] def getLastActivity: Long = lastActivity


  private class BspSessionClient extends BspClient {
    // task notifications
    override def onBuildShowMessage(params: bsp4j.ShowMessageParams): Unit = {
      updateLastActivity()
      val event = ShowMessage(params)
      currentJob.notification(event)
      notifications(event)
    }
    override def onBuildLogMessage(params: bsp4j.LogMessageParams): Unit = {
      updateLastActivity()
      val event = LogMessage(params)
      currentJob.notification(event)
      notifications(event)
    }
    override def onBuildPublishDiagnostics(params: bsp4j.PublishDiagnosticsParams): Unit = {
      updateLastActivity()
      val event = PublishDiagnostics(params)
      currentJob.notification(event)
      notifications(event)
    }

    override def onBuildTaskStart(params: bsp4j.TaskStartParams): Unit = {
      updateLastActivity()
      val event = TaskStart(params)
      currentJob.notification(event)
    }

    override def onBuildTaskProgress(params: bsp4j.TaskProgressParams): Unit = {
      updateLastActivity()
      val event = TaskProgress(params)
      currentJob.notification(event)
    }

    override def onBuildTaskFinish(params: bsp4j.TaskFinishParams): Unit = {
      updateLastActivity()
      val event = TaskFinish(params)
      currentJob.notification(event)
    }

    // build-level notifications
    override def onConnectWithServer(server: bsp4j.BuildServer): Unit = super.onConnectWithServer(server)

    override def onBuildTargetDidChange(didChange: bsp4j.DidChangeBuildTarget): Unit = {
      updateLastActivity()
      val event = DidChangeBuildTarget(didChange)
      notifications(event)
    }

    private def updateLastActivity(): Unit = {
      lastActivity = System.currentTimeMillis()
    }

  }

  private class BspProcessMessageHandler(input: InputStream) extends Callable[Unit] {

    override def call(): Unit = {
      val lines = Source.fromInputStream(input).getLines()
      lines.foreach { message =>
        lastProcessOutput = System.currentTimeMillis()
        lastActivity = lastProcessOutput
        //noinspection ScalaExtractStringToBundle,ReferencePassedToNls
        currentJob.log(message + '\n')
      }
    }
  }
}

object BspSession {

  type ProcessLogger = String => Unit
  type NotificationAggregator[A] = (A, BspNotification) => A
  type NotificationCallback = BspNotification => Unit
  type BspSessionTask[T] = (BspServer, BuildServerInfo) => CompletableFuture[T]

  /**
   * @param displayName corresponds to [[ch.epfl.scala.bsp4j.InitializeBuildResult#getDisplayName]]
   */
  case class BuildServerInfo(
    displayName: String,
    capabilities: BuildServerCapabilities
  )

  trait BspServer extends bsp4j.BuildServer
    with bsp4j.ScalaBuildServer
    with bsp4j.JavaBuildServer
    with bsp4j.JvmBuildServer
  trait BspClient extends bsp4j.BuildClient

  private[protocol] def builder(
    bspPID: Long,
    bspIn: InputStream,
    bspErr: InputStream,
    bspOut: OutputStream,
    initializeBuildParams: bsp4j.InitializeBuildParams,
    cleanup: ()=>Unit): Builder = {

    new Builder(bspPID, bspIn, bspErr, bspOut, initializeBuildParams, cleanup)
  }

  private[protocol] class Builder private[BspSession](
     bspPID: Long,
     bspIn: InputStream,
     bspErr: InputStream,
     bspOut: OutputStream,
     initializeBuildParams: bsp4j.InitializeBuildParams,
     cleanup: ()=>Unit) {

    private var notificationCallbacks: List[NotificationCallback] = Nil
    private var initialJob: BspSessionJob[?,?] = DummyJob
    private var traceLogPredicate: () => Boolean = () => false

    def addNotificationCallback(callback: NotificationCallback): Builder = {
      notificationCallbacks ::= callback
      this
    }

    def withInitialJob(job: BspSessionJob[?,?]): Builder = {
      initialJob = job
      this
    }

    def withTraceLogPredicate(pred: () => Boolean): Builder = {
      traceLogPredicate = pred
      this
    }

    def create = new BspSession(
      bspPID,
      bspIn,
      bspErr,
      bspOut,
      initializeBuildParams,
      cleanup,
      notificationCallbacks,
      initialJob,
      traceLogPredicate
    )
  }


  private case class ServerConnection(server: BspServer, cancelable: Cancelable, listening: java.util.concurrent.Future[Void])

  /**
   * Calls [[com.intellij.execution.process.OSProcessUtil#terminateProcessGracefully(int)]]. Falls back to
   * [[java.lang.ProcessHandle#destroy()]] if graceful termination cannot be attempted.
   *
   * Then waits for the process to exit until the supplied timeout argument.
   */
  private[session] def terminateProcessGracefully(handle: ProcessHandle, timeout: FiniteDuration): Unit = {
    try OSProcessUtil.terminateProcessGracefully(handle.pid().toInt)
    catch {
      case _: UnsupportedOperationException => handle.destroy()
    }
    handle.onExit().get(timeout.length, timeout.unit)
  }
}
