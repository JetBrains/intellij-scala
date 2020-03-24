package org.jetbrains.jps.incremental.scala
package remote

import java.net.{ConnectException, InetAddress, UnknownHostException}

import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilationData, CompilerData, SbtData}
import org.jetbrains.plugins.scala.server.CompileServerToken

/**
 * @author Pavel Fatin
 */
class RemoteServer(override val address: InetAddress, override val port: Int)
  extends Server
    with RemoteResourceOwner {

  override def compile(sbtData: SbtData,
                       compilerData: CompilerData,
                       compilationData: CompilationData,
                       client: Client): ExitCode = {
    val token = CompileServerToken.tokenForPort(port).getOrElse("NO_TOKEN")

    val arguments = Arguments(token, sbtData, compilerData, compilationData, None).asStrings

    try {
      send(CommandIds.Compile, arguments, client)
      // client.compilationEnd() is meant to be sent by remote server
      ExitCode.OK
    } catch {
      case e: ConnectException =>
        val firstLine = s"Cannot connect to compile server at ${address.toString}:$port"
        val secondLine = "Trying to compile without it"
        val message = s"$firstLine\n$secondLine"
        client.warning(message)
        client.internalInfo(s"$firstLine\n${e.toString}\n${e.getStackTrace.mkString("\n")}")
        ScalaBuilder.localServer.compile(sbtData, compilerData, compilationData, client)
      case e: UnknownHostException =>
        val message = "Unknown IP address of compile server host: " + address.toString
        client.error(message)
        client.internalInfo(s"$message\n${e.toString}\n${e.getStackTrace.mkString("\n")}")
        ExitCode.ABORT
    }
  }
}