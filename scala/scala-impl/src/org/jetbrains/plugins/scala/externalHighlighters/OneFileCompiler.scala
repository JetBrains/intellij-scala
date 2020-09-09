package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.plugins.scala.compiler.{RemoteServerConnectorBase, RemoteServerRunner}

object OneFileCompiler {

  /**
   * Compiles one file without output.
   * 
   * @param source the source file to compile.
   * @param module the module containing source file
   */
  def compile(source: File, module: Module, client: Client): Unit = {
    val outputDir = FileUtil.createTempDirectory("tempOutputDir", null, false)
    try {
      new RemoteServerConnector(source, module, outputDir).compile(client)
    } finally {
      FileUtil.delete(outputDir)
    }
  }
  
  private class RemoteServerConnector(source: File,
                                      module: Module,
                                      outputDir: File)
    extends RemoteServerConnectorBase(module, Some(Seq(source)), outputDir) {
    
    def compile(client: Client): Unit =
      new RemoteServerRunner(module.getProject)
        .buildProcess(CommandIds.Compile, arguments.asStrings, client)
        .runSync() 
  }
}
