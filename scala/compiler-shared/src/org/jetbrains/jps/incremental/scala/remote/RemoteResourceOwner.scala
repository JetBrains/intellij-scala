package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.incremental.scala.*
import org.jetbrains.plugins.scala.server.CompileServerPort

import java.io.{BufferedInputStream, BufferedOutputStream, DataInputStream, DataOutputStream}
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Using

trait RemoteResourceOwner {

  protected def address: InetAddress

  protected def compileServerPort: CompileServerPort

  protected def socketConnectTimeout: FiniteDuration = 10.seconds

  protected val currentDirectory: String = System.getProperty("user.dir")

  @throws[java.io.IOException]
  @throws[java.net.ConnectException] // e.g. "connection reset" (e.g. when the server shutdown while we are reading from the socket)
  @throws[java.net.SocketException] // e.g. "connection refused" (e.g. when creating the socket and the service is unavailable)
  @throws[java.net.SocketTimeoutException] // e.g. if socket connection "handshake" exceeds some timeout
  @throws[java.net.UnknownHostException]
  def send(command: String, arguments: Seq[String], client: Client): Unit = {
    client.internalTrace(s"sending command to server: `$command`")
    val socket = new Socket()
    val socketAddress = new InetSocketAddress(address, compileServerPort.forCommunication)
    socket.connect(socketAddress, socketConnectTimeout.toMillis.toInt)
    client.internalTrace(s"socket connected")

    Using.resource(socket) { socket =>
      Using.resource(new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))) { output =>
        val chunks = createChunks(command, arguments)
        client.internalTrace(s"writing chunks to socket")
        chunks.foreach(_.writeTo(output))
        output.flush()

        if (client != null) {
          Using.resource(new DataInputStream(new BufferedInputStream(socket.getInputStream))) { input =>
            client.internalTrace("reading chunks from socket")
            // Returning from the `handle` (for example, on cancel) unwinds Using.resource scopes and closes the socket
            handle(input, client)
          }
        }
      }
    }
  }

  protected def handle(input: DataInputStream, client: Client): Unit = {
    val processor = new ClientEventProcessor(client)

    // When a client is canceled, we stop reading from the socket in the loop.
    // The caller then closes the socket via Using.resource
    while (!client.isCanceled) {
      val chunk = Chunk.readFrom(input)
      chunk match {
        case Chunk(NailgunConstants.CHUNKTYPE_EXIT, code) =>
          return
        case Chunk(NailgunConstants.CHUNKTYPE_STDOUT, data) =>
          try {
            val event = Event.fromBytes(Base64.getDecoder.decode(data))
            processor.process(event)
          } catch {
            case e: Exception =>
              val chars = {
                val s = new String(data, StandardCharsets.UTF_8)
                if (s.length > 50) s.substring(0, 50) + "..." else s
              }
              client.error("Unable to read an event from: " + chars)
              client.trace(e)
          }
        // Main server class redirects all (unexpected) stdout data to stderr.
        // In theory, there should be no such data at all, however, in practice,
        // sbt "leaks" some messages into console (e.g. for "explain type errors" option).
        // For example such errors occur during compilation errors of worksheet ILoopWrapper instances.
        // Report such output not as errors, but as warnings (to continue make process).
        //
        // Also sometimes Nailgun prints to NGContext output (instead of default process output)
        // e.g. see com.facebook.nailgun.builtins.DefaultNail.nailMain
        case Chunk(NailgunConstants.CHUNKTYPE_STDERR, data) =>
          val message = fromBytes(data)
          if (isNotBlank(message)) {
            val messageClean = RemoteResourceOwner.ansiColorCodePattern.replaceAllIn(message, "")
            client.warning(messageClean)
          }
        case Chunk(kind, data) =>
          client.error(s"Unexpected server output of kind $kind: ${new String(data, StandardCharsets.UTF_8)}")
      }
    }
  }

  /**
   * An exact reimplementation of `org.apache.commons.lang3.StringUtils.isNotBlank` to avoid having to ship
   * the Apache `commons-lang3` on the JPS classpath.
   */
  private def isNotBlank(@Nullable str: String): Boolean = !isBlank(str)

  /**
   * An exact reimplementation of `org.apache.commons.lang3.StringUtils.isBlank` to avoid having to ship
   * the Apache `commons-lang3` on the JPS classpath.
   */
  private def isBlank(@Nullable str: String): Boolean =
    str == null || str.forall(_.isWhitespace)

  protected def createChunks(command: String, args: Seq[String]): Seq[Chunk] = {
    args.map(s => Chunk(NailgunConstants.CHUNKTYPE_ARGUMENT.toChar, toBytes(s))) :+
      Chunk(NailgunConstants.CHUNKTYPE_WORKINGDIRECTORY.toChar, toBytes(currentDirectory)) :+
      Chunk(NailgunConstants.CHUNKTYPE_COMMAND.toChar, toBytes(command))
  }

  private def toBytes(s: String) = s.getBytes(StandardCharsets.UTF_8)

  private def fromBytes(bytes: Array[Byte]) = new String(bytes, StandardCharsets.UTF_8)
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