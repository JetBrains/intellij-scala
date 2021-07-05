package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, CompilerData, SbtData}

import java.net.{ConnectException, InetAddress, UnknownHostException}

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
    final case class ConnectionError(address: InetAddress, port: Int, cause: ConnectException) extends ServerError
    final case class UnknownHost(address: InetAddress, cause: UnknownHostException) extends ServerError
  }
}