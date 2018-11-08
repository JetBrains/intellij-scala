package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.UUID

import com.intellij.compiler.server.{BuildManagerListener, CustomBuilderMessageHandler}
import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerTopics}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugin.scala.compilerReferences.{ScalaCompilerReferenceIndexBuilder => Builder}
import org.jetbrains.plugins.scala.indices.protocol.IdeaIndicesJsonProtocol._
import org.jetbrains.plugins.scala.indices.protocol.jps.JpsCompilationInfo
import spray.json._

import scala.util.Try

private class JpsCompilationWatcher(
  override val project:                       Project,
  protected override val currentCompilerMode: () => CompilerMode
) extends CompilationWatcher[CompilerMode.JPS.type] { self =>
  import JpsCompilationWatcher._

  override def compilerMode: CompilerMode.JPS.type = CompilerMode.JPS

  private[this] def handleBuilderMessage(messageType: String, messageText: String): Unit = {
    logger.debug(s"Received compiler index builder message: $messageType: $messageText.")

    messageType match {
      case Builder.compilationDataType =>
        val buildData = Try(messageText.parseJson.convertTo[JpsCompilationInfo])

        buildData.fold(
          error => {
            logger.error("Malformed messageText from builder.", error)
            eventPublisher.onError()
          },
          data => {
            val modules = data.affectedModules
            logger.debug(s"Scheduled building index for modules ${modules.mkString("[", ", ", "]")}")
            eventPublisher.processCompilationInfo(data)
          }
        )
      case Builder.compilationStartedType =>
        val isCleanBuild = Try(messageText.parseJson.convertTo[Boolean]).getOrElse(false)
        eventPublisher.startIndexing(isCleanBuild)
      case Builder.compilationFinishedType =>
        val timestamp = Try(messageText.parseJson.convertTo[Long]).getOrElse(-1L)
        eventPublisher.finishIndexing(timestamp)
    }
  }

  override def start(): Unit = {
    val connection = project.getMessageBus.connect(project)

    connection.subscribe(
      CustomBuilderMessageHandler.TOPIC,
      new CustomBuilderMessageHandler {
        override def messageReceived(
          builderId: String,
          messageType: String,
          messageText: String
        ): Unit = if (isEnabled && builderId == Builder.id) handleBuilderMessage(messageType, messageText)
      }
    )

    connection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener {
      override def buildStarted(
        project:    Project,
        sessionId:  UUID,
        isAutomake: Boolean
      ): Unit = if (isEnabled && project == self.project) eventPublisher.onCompilationStart()
    })

    connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener {
      override def compilationFinished(
        aborted:        Boolean,
        errors:         Int,
        warnings:       Int,
        compileContext: CompileContext
      ): Unit = if (aborted || errors != 0) eventPublisher.finishIndexing(-1)

      override def automakeCompilationFinished(
        errors:         Int,
        warnings:       Int,
        compileContext: CompileContext
      ): Unit = if (errors != 0) eventPublisher.finishIndexing(-1)
    })
  }
}

object JpsCompilationWatcher {
  private val logger = Logger.getInstance(classOf[JpsCompilationWatcher])
}
