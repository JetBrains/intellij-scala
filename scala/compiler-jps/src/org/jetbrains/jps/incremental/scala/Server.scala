package org.jetbrains.jps.incremental.scala

import org.jetbrains.plugins.scala.compiler.data.{CompilationData, CompilerData, SbtData}

import java.net.{InetAddress, SocketException, SocketTimeoutException, UnknownHostException}
import scala.concurrent.duration.FiniteDuration

trait Server {

  def compile(
    sbtData: SbtData,
    compilerData: CompilerData,
    compilationData: CompilationData,
    client: Client
  ): Either[Server.ServerError, ExitCode]
}

object Server {
  sealed trait ServerError
  object ServerError {
    final case class SocketConnectTimeout(address: InetAddress, port: Int, timeout: FiniteDuration, cause: SocketTimeoutException) extends ServerError
    final case class ConnectionError(address: InetAddress, port: Int, cause: SocketException) extends ServerError
    final case class UnknownHost(address: InetAddress, cause: UnknownHostException) extends ServerError
    final case class MissingScalaCompileServerSystemDirectory(cause: MissingScalaCompileServerSystemDirectoryException) extends ServerError

    class MissingScalaCompileServerSystemDirectoryException(msg: String) extends Exception(msg)
  }
}