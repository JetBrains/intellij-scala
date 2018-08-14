package org.jetbrains.bsp.protocol

import ch.epfl.scala.bsp._
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.jetbrains.bsp.BspUtil.IdeaLoggerOps
import org.jetbrains.bsp.bsp

import scala.meta.jsonrpc._

class BspSession(messages: Observable[BaseProtocolMessage],
                 private implicit val client: LanguageClient,
                 initializedBuildParams: InitializeBuildParams,
                 cleanup: Task[Unit]
                ) {

  private val logger = Logger.getInstance(classOf[BspCommunication])

  /** Task starts client-server connection and connects message stream.
    * @param services services that are used to listen to build notifications.
    */
  def run[T](services: Services, task: LanguageClient => Task[T])(implicit scheduler: Scheduler): Task[T] = {
    val runningClientServer = startClientServer(services)

    val whenDone = {
      val shutdownRequest = for {
        shutdown <- endpoints.Build.shutdown.request(Shutdown())
      } yield {
        shutdown match {
          case Left(Response.Error(err, id)) =>
            bsp.balloonNotification.createNotification(err.message, NotificationType.ERROR)
            val fullMessage = s"${err.message} (code ${err.code}). Data: ${err.data.getOrElse("{}")}"
            logger.error(fullMessage)
          case _ =>
        }
        endpoints.Build.exit.notify(Exit())
      }

      val cleaning = Task {
        logger.debug("closing bsp connection")
        runningClientServer.cancel()
      }

      for {
        _ <- shutdownRequest
        _ <- cleaning
        // TODO check process state, hard-kill bsp process if shutdown was not orderly
      } yield ()
    }

    val resultTask = for {
      initResult <- initRequest
      // TODO handle initResult.capabilities
      _ = endpoints.Build.initialized.notify(InitializedBuildParams())
      result <- task(client)
    } yield {
      result
    }

    resultTask
      .doOnCancel(whenDone)
      .doOnFinish {
        case Some(err) =>
          logger.error("bsp connection error", err)
          whenDone
        case None => whenDone
      }
  }

  private val initRequest =
    endpoints.Build.initialize.request(initializedBuildParams)

  private def startClientServer(services: Services)(implicit scheduler: Scheduler) = {
    val server = new LanguageServer(messages, client, services, scheduler, logger.toScribeLogger)
    server.startTask
      .doOnFinish { errOpt =>
        for {
          cleaned <- cleanup
        } yield {
          logger.debug("client/server closed")
          errOpt.foreach { err =>
            logger.warn(s"client/server closed with error: $err")
          }
        }
      }
      .doOnCancel(cleanup)
      .runAsync
  }

}
