package org.jetbrains.jps.incremental.scala.remote

import java.io._
import java.net.{InetAddress, Socket}

import com.martiansoftware.nailgun.NGConstants
import org.jetbrains.jps.incremental.scala._

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov
 */
trait RemoteResourceOwner {

  protected val address: InetAddress
  protected val port: Int
  
  protected val currentDirectory = System.getProperty("user.dir")
  protected val serverAlias = "compile-server"

  def send(command: String, arguments: Seq[String], client: Client) {
    using(new Socket(address, port)) { socket =>
      using(new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))) { output =>
        createChunks(command, arguments).foreach(_.writeTo(output))
        output.flush()
        if (client != null) {
          using(new DataInputStream(new BufferedInputStream(socket.getInputStream))) { input =>
            handle(input, client)
          }
        }
      }
    }
  }

  protected def handle(input: DataInputStream, client: Client) {
    val processor = new ClientEventProcessor(client)

    while (!client.isCanceled) {
      Chunk.readFrom(input) match {
        case Chunk(NGConstants.CHUNKTYPE_EXIT, code) =>
          return
        case Chunk(NGConstants.CHUNKTYPE_STDOUT, data) =>
          try {
            val event = Protocol.deserializeEvent(data)
            processor.process(event)
          } catch {
            case e: Exception =>
              val chars = {
                val s = new String(data)
                if (s.length > 50) s.substring(0, 50) + "..." else s
              }
              client.error("Unable to read an event from: " + chars)
              client.trace(e)
          }
        // Main server class redirects all (unexpected) stdout data to stderr.
        // In theory, there should be no such data at all, however, in practice,
        // sbt "leaks" some messages into console (e.g. for "explain type errors" option).
        // Report such output not as errors, but as warnings (to continue make process).
        case Chunk(NGConstants.CHUNKTYPE_STDERR, data) =>
          client.warning(Protocol.fromBytes(data))
        case Chunk(kind, data) =>
          client.error("Unexpected server output: " + data)
      }
    }
  }

  protected def createChunks(command: String, args: Seq[String]): Seq[Chunk] = {
    val serializedArgs = Protocol.serializeArgs(args)
    val argsChunks = Chunk(NGConstants.CHUNKTYPE_ARGUMENT.toChar, serializedArgs)
    val cwdChunk = Chunk(NGConstants.CHUNKTYPE_WORKINGDIRECTORY.toChar, Protocol.toBytes(currentDirectory))
    val commandChunk = Chunk(NGConstants.CHUNKTYPE_COMMAND.toChar, Protocol.toBytes(command))
    Seq(argsChunks, cwdChunk, commandChunk)
  }

}

case class Chunk(kind: Chunk.Kind, data: Array[Byte]) {
  def writeTo(output: DataOutputStream) {
    output.writeInt(data.length)
    output.writeByte(kind.toByte)
    output.write(data)
  }
}

object Chunk {
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