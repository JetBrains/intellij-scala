package org.jetbrains.plugins.scala.compiler.oneFile

import java.io.File

import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.scala.{Client, DummyClient}
import org.jetbrains.plugins.scala.compiler.oneFile.RemoteServerConnector.CollectingMessagesClient
import org.jetbrains.plugins.scala.compiler.{RemoteServerConnectorBase, RemoteServerRunner}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

/**
 * @param module module that contains specified source file
 * @param sourceFile source file to compile
 * @param outputDir output directory. If not specified, target files will not be generated.
 */
private[oneFile]
class RemoteServerConnector(module: Module,
                            sourceFile: File,
                            outputDir: Option[File])
  extends RemoteServerConnectorBase(module, Seq(sourceFile), outputDir.getOrElse(new File(""))) {

  override protected def additionalScalaParameters: Seq[String] = outputDir match {
    case Some(_) => Seq.empty
    case None => Seq("-Ystop-after:typer")
  }

  /**
   * Compiles specified file and returns all compiler messages.
   */
  def compile(): Seq[Client.ClientMsg] = {
    val project = module.getProject
    val client = new CollectingMessagesClient
    val compilationProcess = new RemoteServerRunner(project).buildProcess(arguments, client)
    val result = Promise[Seq[Client.ClientMsg]]
    compilationProcess.addTerminationCallback {
      case Some(error) => throw error
      case None => result.success(client.collectedMessages)
    }
    compilationProcess.run()
    Await.result(result.future, Duration.Inf)
  }
}

object RemoteServerConnector {

  private class CollectingMessagesClient extends DummyClient {

    private val messages = mutable.Buffer[Client.ClientMsg]()

    override def message(msg: Client.ClientMsg): Unit =
      messages += msg

    def collectedMessages: Seq[Client.ClientMsg] =
      messages.toList
  }
}
