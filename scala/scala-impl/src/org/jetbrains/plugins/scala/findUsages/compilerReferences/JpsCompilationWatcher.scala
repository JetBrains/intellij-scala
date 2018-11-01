package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.UUID

import com.intellij.compiler.impl.ExitStatus
import com.intellij.compiler.server.{BuildManagerListener, CustomBuilderMessageHandler}
import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerTopics}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.plugin.scala.compilerReferences.{ScalaCompilerReferenceIndexBuilder => Builder}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.CompilationWatcher.CompilerIndicesState
import org.jetbrains.plugins.scala.indices.protocol.IdeaIndicesJsonProtocol._
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo
import spray.json._

import scala.util.Try

private class JpsCompilationWatcher(
  override val project:            Project,
  override val transactionManager: TransactionManager[CompilerIndicesState]
) extends CompilationWatcher[CompilerMode.JPS.type] { self =>
  import JpsCompilationWatcher._

  override def compilerMode: CompilerMode.JPS.type = CompilerMode.JPS

  private[this] def handleBuilderMessage(
    messageType: String,
    messageText: String,
    publisher:   CompilerIndicesEventPublisher
  ): Unit = {
    logger.debug(s"Received compiler index builder message: $messageType: $messageText.")

    messageType match {
      case Builder.compilationDataType =>
        val buildData = Try(messageText.parseJson.convertTo[JpsCompilationInfo])

        buildData.fold(
          error => {
            logger.error(s"Malformed messageText from builder: $messageText", error)
            publisher.onError()
          },
          publisher.processCompilationInfo(_, offline = false)
        )
      case Builder.compilationStartedType =>
        val isCleanBuild = Try(messageText.parseJson.convertTo[Boolean]).getOrElse(false)
        publisher.startIndexing(isCleanBuild)
      case Builder.compilationFinishedType => publisher.finishIndexing()
      case _                               => ()
    }
  }

  override def start(): Unit = {
    val connection = project.getMessageBus.connect(project)

    connection.subscribe(
      CustomBuilderMessageHandler.TOPIC,
      new CustomBuilderMessageHandler {
        override def messageReceived(
          builderId:   String,
          messageType: String,
          messageText: String
        ): Unit =
          if (builderId == Builder.id) processEventInTransaction(handleBuilderMessage(messageType, messageText, _))
      }
    )

    /* HACK */
    /* in case of a compilation of an up-to-date module
    *  we do not receive any builder events (since no builders are actually executed)
    *  but we still have to mark the module as clean in DirtyScopeHolder afterwards */
    connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener {
      override def compilationFinished(
        aborted:        Boolean,
        errors:         Int,
        warnings:       Int,
        compileContext: CompileContext
      ): Unit = {
        val timestamp   = System.currentTimeMillis()
        val key         = Key.findKeyByName("COMPILE_SERVER_BUILD_STATUS")
        val wasUpToDate = compileContext.getUserData(key) == ExitStatus.UP_TO_DATE
        val modules     = compileContext.getCompileScope.getAffectedModules.map(_.getName)

        processEventInTransaction { publisher =>
          if (wasUpToDate) {
            publisher.onCompilationStart()
            val info = JpsCompilationInfo(modules.toSet, Set.empty, Set.empty, timestamp)
            publisher.processCompilationInfo(info, offline = false)
            publisher.onCompilationFinish()
          }
        }
      }
    })
    /* HACK */

    connection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener {
      override def buildStarted(
        project:    Project,
        sessionId:  UUID,
        isAutomake: Boolean
      ): Unit = if (project == self.project) processEventInTransaction(_.onCompilationStart())

      override def buildFinished(
        project:    Project,
        sessionId:  UUID,
        isAutomake: Boolean
      ): Unit = if (project == self.project) processEventInTransaction(_.onCompilationFinish())
    })
  }
}

object JpsCompilationWatcher {
  private val logger = Logger.getInstance(classOf[JpsCompilationWatcher])
}
