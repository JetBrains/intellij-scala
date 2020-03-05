package org.jetbrains.plugins.scala.externalHighlighters.compiler

import java.io.File

import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.plugins.scala.compiler.{RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.project.ModuleExt

/**
 * @param module module that contains specified source file
 * @param sourceFileOriginal original source file.
 * @param sourceFileCopy source file copy. Note: This file will be compiled!
 */
private[compiler] class DocumentRemoteServerConnector(
  module: Module,
  sourceFileOriginal: File,
  sourceFileCopy: File
) extends RemoteServerConnectorBase(
  module,
  Some(Seq(sourceFileCopy)),
  new File("")
) {

  override protected def additionalScalaParameters: Seq[String] = {
    val lastPhase = if (module.hasScala3) "reifyQuotes" else "patmat"
    Seq(s"-Ystop-after:$lastPhase")
  }

  import DocumentRemoteServerConnector.ReplacedSourcesClient

  /**
   * Compiles specified file and returns all compiler messages.
   */
  def compile(): Unit = {
    val project = module.getProject
    val client = new CompilationClient(project)
      with ReplacedSourcesClient {
      override protected def originalSource: File = sourceFileOriginal
    }
    try {
      new RemoteServerRunner(project).buildProcess(CommandIds.Compile, argumentsRaw, client).runSync()
    } finally {
      sourceFileCopy.delete()
    }
  }
}

object DocumentRemoteServerConnector {

  private trait ReplacedSourcesClient
    extends Client {

    protected def originalSource: File

    abstract override def message(msg: Client.ClientMsg): Unit =
      super.message(msg.copy(source = Some(originalSource)))

    abstract override def generated(source: File,
                                    module: File,
                                    name: String): Unit =
      super.generated(originalSource, module, name)

    abstract override def compilationEnd(sources: Set[File]): Unit =
      super.compilationEnd(Set(originalSource))
  }
}
