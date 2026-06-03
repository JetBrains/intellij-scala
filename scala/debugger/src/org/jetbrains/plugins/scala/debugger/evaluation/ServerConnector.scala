package org.jetbrains.plugins.scala
package debugger
package evaluation

import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.scala.remote.CommandIds
import org.jetbrains.jps.incremental.scala.{Client, DummyClient, MessageKind}
import org.jetbrains.plugins.scala.compiler.{EelPathTranslator, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path
import scala.annotation.tailrec

private final class ServerConnector(module: Module, filesToCompile: Seq[Path], outputDir: Path)
  extends RemoteServerConnectorBase(module, Some(filesToCompile), outputDir) {

  private val errors = Seq.newBuilder[NlsString]

  private val client: Client = new DummyClient {
    override def message(msg: Client.ClientMsg): Unit =
      if (msg.kind == MessageKind.Error) errors += NlsString(msg.text)
  }

  @tailrec
  private def classfiles(dir: Path, namePrefix: String = ""): Array[(Path, String)] = dir.children().toArray match {
    case Array(d) if d.isDirectory => classfiles(d, s"$namePrefix${d.getFileName}.")
    case files => files.map(f => (f, s"$namePrefix${f.getFileName}".stripSuffix(".class")))
  }

  type CompileResult = Either[Seq[NlsString], Array[(Path, String)]]
  def compile(): CompileResult = {
    val compilationProcess = new RemoteServerRunner(module.getProject).buildProcess(CommandIds.Compile, arguments.asStrings(EelPathTranslator), client)
    var result: CompileResult = Left(Seq(NlsString(DebuggerBundle.message("compilation.failed"))))
    compilationProcess.addTerminationCallback { _ => // TODO: do not ignore possible exception
      val foundErrors = errors.result()
      result = if (foundErrors.nonEmpty) Left(foundErrors) else Right(classfiles(outputDir))
    }
    compilationProcess.run()
    result
  }
}
