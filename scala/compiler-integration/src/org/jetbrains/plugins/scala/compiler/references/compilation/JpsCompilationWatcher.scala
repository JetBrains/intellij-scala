package org.jetbrains.plugins.scala.compiler.references.compilation

import com.intellij.compiler.server.{BuildManagerListener, CustomBuilderMessageHandler}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceService.CompilerIndicesState
import org.jetbrains.plugins.scala.compiler.references.{Builder, Messages, TransactionGuard}
import org.jetbrains.plugins.scala.compiler.{CompilerIntegrationBundle, executeOnBuildThread}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.project.settings.CompilerMode

import java.util.UUID

private[references] class JpsCompilationWatcher(
  override val project:          Project,
  override val transactionGuard: TransactionGuard[CompilerIndicesState]
) extends CompilationWatcher[CompilerMode.JPS.type] { self =>

  override def compilerMode: CompilerMode.JPS.type = CompilerMode.JPS

  private[this] def handleBuilderMessage(
    messageType: String,
    messageText: String,
    publisher:   CompilerIndicesEventPublisher
  ): Unit =
    messageType match {
      case Messages.compilationDataType =>
        val buildData = Messages.decompressCompilationInfo(messageText)

        buildData.fold(
          error => {
            publisher.onError(CompilerIntegrationBundle.message("malformed.message.from.builder", messageText), Option(error))
          },
          publisher.processCompilationInfo(_, offline = false)
        )
      case Messages.compilationStartedType =>
        val isCleanBuild = java.lang.Boolean.valueOf(messageText)
        publisher.startIndexing(isCleanBuild)
      case Messages.compilationFinishedType => publisher.finishIndexing()
      case _                               => ()
    }

  override def start(): Unit = {
    val connection = project.getMessageBus.connect(project.unloadAwareDisposable)

    connection.subscribe(
      CustomBuilderMessageHandler.TOPIC,
      new CustomBuilderMessageHandler {
        override def messageReceived(
          builderId:   String,
          messageType: String,
          messageText: String
        ): Unit =
          if (builderId == Builder.id)
            processEventInTransaction(handleBuilderMessage(messageType, messageText, _))
      }
    )

    connection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener {
      override def buildStarted(
        project:    Project,
        sessionId:  UUID,
        isAutomake: Boolean
      ): Unit = {
        if (project == self.project) {
          processEventInTransaction { publisher =>
            publisher.onCompilationStart()
          }
        }
      }

      override def buildFinished(
        project:    Project,
        sessionId:  UUID,
        isAutomake: Boolean
      ): Unit = if (project == self.project) {
        executeOnBuildThread { () =>
          processEventInTransaction { publisher =>
            publisher.onCompilationFinish(true)
          }
        }
      }
    })
  }
}
