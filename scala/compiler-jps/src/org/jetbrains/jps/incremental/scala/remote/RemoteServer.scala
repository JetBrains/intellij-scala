package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.scala.Server.ServerError
import org.jetbrains.jps.incremental.scala.{Client, Server}
import org.jetbrains.plugins.scala.compiler.data.{Arguments, CompilationData, CompilerData, SbtData}
import org.jetbrains.plugins.scala.server.CompileServerToken

import java.net.{ConnectException, InetAddress, SocketTimeoutException, UnknownHostException}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class RemoteServer(
  override val address: InetAddress,
  override val port: Int,
  override protected val socketReadTimeout: FiniteDuration
) extends Server
  with RemoteResourceOwner {

  override def compile(sbtData: SbtData,
                       compilerData: CompilerData,
                       compilationData: CompilationData,
                       client: Client): Either[Server.ServerError, ExitCode] = {
    val arguments = Arguments(sbtData, compilerData, compilationData, None).asStrings

    try {
      val buildSystemDir = Utils.getSystemRoot.toPath
      val token = CompileServerToken.tokenForPort(buildSystemDir, port).getOrElse("NO_TOKEN")
      send(CommandIds.Compile, token +: arguments, client)
      // client.compilationEnd() is meant to be sent by remote server
      Right(ExitCode.OK)
    } catch {
      case e: SocketTimeoutException => Left(ServerError.SocketReadTimeout(address, port, socketReadTimeout, e))
      case e: ConnectException       => Left(ServerError.ConnectionError(address, port, e))
      case e: UnknownHostException   => Left(ServerError.UnknownHost(address, e))
    }
  }
}