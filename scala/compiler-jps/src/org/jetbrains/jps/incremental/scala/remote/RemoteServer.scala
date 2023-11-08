package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.incremental.scala.Server.ServerError
import org.jetbrains.jps.incremental.scala.Server.ServerError.MissingScalaCompileServerSystemDirectoryException
import org.jetbrains.jps.incremental.scala.{Client, ExitCode, Server}
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilationData, CompilerData, ComputeStampsArguments, SbtData}
import org.jetbrains.plugins.scala.server.CompileServerToken

import java.net.{InetAddress, SocketException, SocketTimeoutException, UnknownHostException}
import java.nio.file.{Path, Paths}
import scala.concurrent.duration.FiniteDuration

final class RemoteServer(
                          override val address: InetAddress,
                          override val port: Int,
                          override protected val socketConnectTimeout: FiniteDuration
                        ) extends Server
  with RemoteResourceOwner {

  override def compile(sbtData: SbtData,
                       compilerData: CompilerData,
                       compilationData: CompilationData,
                       client: Client): Either[Server.ServerError, ExitCode] = {
    val arguments = Arguments(sbtData, compilerData, compilationData, None).asStrings
    sendCommand(CommandIds.Compile, arguments, client)
  }

  override def computeStamps(outputFiles: Seq[Path], analysisFile: Path, client: Client): Either[Server.ServerError, ExitCode] = {
    val arguments = ComputeStampsArguments(outputFiles, analysisFile).asStrings
    sendCommand(CommandIds.ComputeStamps, arguments, client)
  }

  private def sendCommand(command: String, arguments: Seq[String], client: Client): Either[Server.ServerError, ExitCode] = {
    try {
      val scalaCompileServerSystemDir = Option(System.getProperty("scala.compile.server.system.dir"))
        .getOrElse(throw new MissingScalaCompileServerSystemDirectoryException("Scala compile server system directory not provided"))

      client.internalTrace(s"reading token for port: $port")
      val token = CompileServerToken.tokenForPort(Paths.get(scalaCompileServerSystemDir), port).getOrElse("NO_TOKEN")
      send(command, token +: arguments, client)
      // client.compilationEnd() is meant to be sent by remote server
      Right(ExitCode.Ok)
    } catch {
      case e: SocketTimeoutException => Left(ServerError.SocketConnectTimeout(address, port, socketConnectTimeout, e))
      case e: SocketException => Left(ServerError.ConnectionError(address, port, e))
      case e: UnknownHostException => Left(ServerError.UnknownHost(address, e))
      case e: MissingScalaCompileServerSystemDirectoryException => Left(ServerError.MissingScalaCompileServerSystemDirectory(e))
    }
  }
}
