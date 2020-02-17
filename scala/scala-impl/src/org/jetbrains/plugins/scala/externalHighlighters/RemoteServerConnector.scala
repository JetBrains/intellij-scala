package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.util.CompilationId

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

/**
 * @param module module that contains specified source file
 * @param sourceFileOriginal original source file.
 * @param sourceFileCopy source file copy. Note: This file will be compiled!
 * @param outputDir output directory. If not specified, target files will not be generated.
 */
private[externalHighlighters]
class RemoteServerConnector(module: Module,
                            sourceFileOriginal: File,
                            sourceFileCopy: File,
                            outputDir: Option[File])
  extends RemoteServerConnectorBase(module, Seq(sourceFileCopy), outputDir.getOrElse(new File(""))) {

  override protected def additionalScalaParameters: Seq[String] = outputDir match {
    case Some(_) => Seq.empty
    case None => Seq("-Ystop-after:patmat")
  }

  /**
   * Compiles specified file and returns all compiler messages.
   */
  def compile(): Unit = {
    val project = module.getProject
    val client = new CompilationClient(CompilationId.generate())
    val compilationProcess = new RemoteServerRunner(project).buildProcess(arguments, client)
    val result = Promise[Unit]
    compilationProcess.addTerminationCallback {
      case Some(error) => throw error
      case None => result.success(())
    }
    compilationProcess.run()
    Await.result(result.future, Duration.Inf)
  }

  private def sendEvent(event: CompilerEvent): Unit =
    module.getProject.getMessageBus
      .syncPublisher(CompilerEventListener.topic)
      .eventReceived(event)

  private class CompilationClient(compilationId: CompilationId)
    extends DummyClient {

    override def message(msg: Client.ClientMsg): Unit = {
      val fixedMsg = msg.copy(source = Some(sourceFileOriginal))
      sendEvent(CompilerEvent.MessageEmitted(compilationId, fixedMsg))
    }

    override def compilationEnd(sources: Set[File]): Unit =
      sendEvent(CompilerEvent.CompilationFinished(compilationId, sourceFileOriginal))
  }
}
