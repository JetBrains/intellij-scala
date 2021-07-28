package org.jetbrains.jps.incremental.scala.remote

import com.martiansoftware.nailgun.NGConstants
import org.apache.commons.lang3.StringUtils
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala._

import java.io._
import java.net.{InetAddress, Socket}
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Using

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov
 */
trait RemoteResourceOwner {

  protected def address: InetAddress
  protected def port: Int

  // TODO: set non-zero value and properly handle timeout exception in all usages RemoteResourceOwner
  protected def socketReadTimeout: FiniteDuration = Duration.Zero

  protected val currentDirectory: String = System.getProperty("user.dir")

  @throws[java.io.IOException]
  @throws[java.net.SocketException]
  @throws[java.net.SocketTimeoutException]
  @throws[java.net.UnknownHostException]
  def send(command: String, arguments: Seq[String], client: Client): Unit = {
    client.internalTrace(s"sending command to server: `$command`")
    val encodedArgs = arguments.map(s => Base64.getEncoder.encodeToString(s.getBytes(StandardCharsets.UTF_8)))
    Using.resource(new Socket(address, port)) { socket =>
      Using.resource(new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))) { output =>
        val chunks = createChunks(command, encodedArgs)
        client.internalTrace(s"writing chunks to socket")
        chunks.foreach(_.writeTo(output))
        output.flush()
        if (client != null) {
          Using.resource(new DataInputStream(new BufferedInputStream(socket.getInputStream))) { input =>
            val timoutMs = socketReadTimeout.toMillis
            if (timoutMs > 0) {
              socket.setSoTimeout(timoutMs.toInt)
            }
            client.internalTrace("reading chunks from socket")
            handle(input, client)
          }
        }
      }
    }
  }

  protected def handle(input: DataInputStream, client: Client): Unit = {
    val processor = new ClientEventProcessor(client)

    while (!client.isCanceled) {
      val chunk = Chunk.readFrom(input)
      chunk match {
        case Chunk(NGConstants.CHUNKTYPE_EXIT, code) =>
          return
        case Chunk(NGConstants.CHUNKTYPE_STDOUT, data) =>
          try {
            val event = Event.fromBytes(Base64.getDecoder.decode(data))
            processor.process(event)
          } catch {
            case e: Exception =>
              val chars = {
                val s = new String(data)
                if (s.length > 50) s.substring(0, 50) + "..." else s
              }
              client.message(Kind.ERROR, "Unable to read an event from: " + chars)
              client.trace(e)
          }
        // Main server class redirects all (unexpected) stdout data to stderr.
        // In theory, there should be no such data at all, however, in practice,
        // sbt "leaks" some messages into console (e.g. for "explain type errors" option).
        // For example such errors occur during compilation errors of worksheet ILoopWrapper instances.
        // Report such output not as errors, but as warnings (to continue make process).
        //
        // Also sometimes Nailgun prints to NGContext output (instead of default process output)
        // e.g. see com.martiansoftware.nailgun.builtins.DefaultNail.nailMain
        case Chunk(NGConstants.CHUNKTYPE_STDERR, data) =>
          val message = fromBytes(data)
          if (StringUtils.isNotBlank(message)) {
            val messageClean = RemoteResourceOwner.ansiColorCodePattern.replaceAllIn(message, "")
            client.message(Kind.WARNING, messageClean)
          }
        case Chunk(kind, data) =>
          client.message(Kind.ERROR, s"Unexpected server output of kind $kind: ${new String(data, StandardCharsets.UTF_8)}")
      }
    }
  }

  protected def createChunks(command: String, args: Seq[String]): Seq[Chunk] = {
    args.map(s => Chunk(NGConstants.CHUNKTYPE_ARGUMENT.toChar, toBytes(s))) :+
      Chunk(NGConstants.CHUNKTYPE_WORKINGDIRECTORY.toChar, toBytes(currentDirectory)) :+
      Chunk(NGConstants.CHUNKTYPE_COMMAND.toChar, toBytes(command))
  }

  private def toBytes(s: String) = s.getBytes

  private def fromBytes(bytes: Array[Byte]) = new String(bytes)
}

object RemoteResourceOwner {

  private val ansiColorCodePattern = "\\u001B\\[[\\d*]*m".r
}

case class Chunk(kind: Chunk.Kind, data: Array[Byte]) {
  def writeTo(output: DataOutputStream): Unit = {
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