package org.jetbrains.jps.incremental.scala
package remote

import java.net.{ConnectException, InetAddress, UnknownHostException}
import java.nio.file.{Files, Path, Paths}

import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import org.jetbrains.jps.incremental.scala.data.{CompilationData, CompilerData, SbtData}
import org.jetbrains.jps.incremental.scala.remote.RemoteServer._

/**
 * @author Pavel Fatin
 */
class RemoteServer(val address: InetAddress, val port: Int) extends Server with RemoteResourceOwner {
  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode = {
    val token = readStringFrom(tokenPathFor(port)).getOrElse("NO_TOKEN")

    val arguments = Arguments(token, sbtData, compilerData, compilationData, Seq.empty).asStrings

    try {
      send(serverAlias, arguments, client)
      ExitCode.OK
    } catch {
      case e: ConnectException =>
        val firstLine = s"Cannot connect to compile server at ${address.toString}:$port"
        val secondLine = "Trying to compile without it"
        val message = s"$firstLine\n$secondLine"
        client.warning(message)
        client.debug(s"$firstLine\n${e.toString}\n${e.getStackTrace.mkString("\n")}")
        ScalaBuilder.localServer.compile(sbtData, compilerData, compilationData, client)
      case e: UnknownHostException =>
        val message = "Unknown IP address of compile server host: " + address.toString
        client.error(message)
        client.debug(s"$message\n${e.toString}\n${e.getStackTrace.mkString("\n")}")
        ExitCode.ABORT
    }
  }
}

private object RemoteServer {
  private def readStringFrom(path: Path): Option[String] =
    if (path.toFile.exists) Some(new String(Files.readAllBytes(path))) else None

  private def tokenPathFor(port: Int) =
    Paths.get(System.getProperty("user.home"), ".idea-build", "tokens", port.toString)
}