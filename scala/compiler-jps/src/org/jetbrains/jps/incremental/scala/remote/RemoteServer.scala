package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.incremental.scala.Server.ServerError
import org.jetbrains.jps.incremental.scala.Server.ServerError.MissingScalaCompileServerSystemDirectoryException
import org.jetbrains.jps.incremental.scala.{Client, ExitCode, Server}
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilationData, CompilerData, ComputeStampsArguments, SbtData}
import org.jetbrains.plugins.scala.server.{CompileServerPort, CompileServerProperties, CompileServerToken}

import java.net.{InetAddress, SocketException, SocketTimeoutException, UnknownHostException}
import java.nio.file.{Path, Paths}
import scala.concurrent.duration.FiniteDuration

final class RemoteServer(
  override val address: InetAddress,
  override val compileServerPort: CompileServerPort,
  override protected val socketConnectTimeout: FiniteDuration
) extends Server
  with RemoteResourceOwner {

  override def compile(sbtData: SbtData,
                       compilerData: CompilerData,
                       compilationData: CompilationData,
                       client: Client): Either[Server.ServerError, ExitCode] = {
    // NioPathTranslator can/must be used here because the JPS build system process and the Scala Compile Server
    // are always running in the same (virtual) machine.
    val arguments = Arguments(sbtData, compilerData, compilationData, None).asStrings(NioPathTranslator)
    sendCommand(CommandIds.Compile, arguments, client)
  }

  override def computeStamps(outputFiles: Seq[Path], analysisFile: Path, client: Client): Either[Server.ServerError, ExitCode] = {
    // NioPathTranslator can/must be used here because the JPS build system process and the Scala Compile Server
    // are always running in the same (virtual) machine.
    val arguments = ComputeStampsArguments(outputFiles, analysisFile).asStrings(NioPathTranslator)
    sendCommand(CommandIds.ComputeStamps, arguments, client)
  }

  private def sendCommand(command: String, arguments: Seq[String], client: Client): Either[Server.ServerError, ExitCode] = {
    try {
      val scalaCompileServerSystemDir = Option(System.getProperty(CompileServerProperties.SystemDirectoryProperty))
        .getOrElse(throw new MissingScalaCompileServerSystemDirectoryException("Scala compile server system directory not provided"))

      client.internalTrace(s"reading token for port: $compileServerPort")
      val token = CompileServerToken.tokenForPort(Paths.get(scalaCompileServerSystemDir), compileServerPort.forToken).getOrElse("NO_TOKEN")
      send(command, token +: arguments, client)
      // client.compilationEnd() is meant to be sent by remote server
      Right(ExitCode.Ok)
    } catch {
      case e: SocketTimeoutException => Left(ServerError.SocketConnectTimeout(address, compileServerPort.forCommunication, socketConnectTimeout, e))
      case e: SocketException => Left(ServerError.ConnectionError(address, compileServerPort.forCommunication, e))
      case e: UnknownHostException => Left(ServerError.UnknownHost(address, e))
      case e: MissingScalaCompileServerSystemDirectoryException => Left(ServerError.MissingScalaCompileServerSystemDirectory(e))
    }
  }
}
