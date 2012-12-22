package org.jetbrains.jps.incremental.scala
package remote

import java.io._
import data.{CompilationData, CompilerData, SbtData}
import java.net.{InetAddress, UnknownHostException, ConnectException, Socket}
import com.martiansoftware.nailgun.NGConstants
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode
import com.intellij.util.Base64Converter
import RemoteServer._

/**
 * @author Pavel Fatin
 */
class RemoteServer(address: InetAddress, port: Int) extends Server {
  def compile(sbtData: SbtData, compilerData: CompilerData, compilationData: CompilationData, client: Client): ExitCode = {
    val arguments = {
      val strings = Arguments(sbtData, compilerData, compilationData).asStrings
      strings.map(s => Base64Converter.encode(s.getBytes("UTF-8")))
    }

    try {
      send(ServerAlias, arguments, client)
      ExitCode.OK
    } catch {
      case e: ConnectException =>
        val message = "Cannot connect to compile server at %s:%s".format(address.toString, port)
        client.error(message)
        ExitCode.ABORT
      case e: UnknownHostException =>
        val message = "Unknown IP address of compile server host: " + address.toString
        client.error(message)
        ExitCode.ABORT
    }
  }

  private def send(command: String, arguments: Seq[String], client: Client) {
    using(new Socket(address, port)) { socket =>
      using(new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))) { output =>
        createChunks(command, arguments).foreach(_.writeTo(output))
        output.flush()
        using(new DataInputStream(new BufferedInputStream(socket.getInputStream))) { input =>
          handle(input, client)
        }
      }
    }
  }
}

private object RemoteServer {
  private val ServerAlias = "compile-server"

  private val CurrentDirectory = System.getProperty("user.dir")

  private def createChunks(command: String, args: Seq[String]): Seq[Chunk] = {
    args.map(s => Chunk(NGConstants.CHUNKTYPE_ARGUMENT, toBytes(s))) :+
            Chunk(NGConstants.CHUNKTYPE_WORKINGDIRECTORY, toBytes(CurrentDirectory)) :+
            Chunk(NGConstants.CHUNKTYPE_COMMAND, toBytes(command))
  }

  private def toBytes(s: String) = s.getBytes

  private def fromBytes(bytes: Array[Byte]) = new String(bytes)

  private def handle(input: DataInputStream, client: Client) {
    val processor = new ClientEventProcessor(client)

    while (!client.isCanceled) {
      Chunk.readFrom(input) match {
        case Chunk(NGConstants.CHUNKTYPE_EXIT, code) =>
          return
        case Chunk(NGConstants.CHUNKTYPE_STDOUT, data) =>
          try {
            val event = Event.fromBytes(Base64Converter.decode(data))
            processor.process(event)
          } catch {
            case e: Exception =>
              client.message(Kind.ERROR, "Unable to read an event from: " + new String(data))
              client.trace(e)
          }
        case Chunk(NGConstants.CHUNKTYPE_STDERR, data) =>
          client.message(Kind.ERROR, fromBytes(data))
        case Chunk(kind, data) =>
          client.message(Kind.ERROR, "Unexpected server output: " + data)
      }
    }
  }
}

private case class Chunk(kind: Chunk.Kind, data: Array[Byte]) {
  def writeTo(output: DataOutputStream) {
    output.writeInt(data.length)
    output.writeByte(kind.toByte)
    output.write(data)
  }
}

private object Chunk {
  type Kind = Char

  def readFrom(input: DataInputStream): Chunk = {
    val size = input.readInt()
    val kind = input.readByte().toChar
    val data = {
      val buffer = new Array[Byte](size)
      input.readFully(buffer)
      buffer
    }
    Chunk(kind, data)
  }
}
