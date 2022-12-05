package org.jetbrains.jps.incremental.scala.remote

import org.jetbrains.jps.incremental.scala.remote.EncodingEventGeneratingClient._
import org.jetbrains.jps.incremental.scala.{Client, MessageKind}

import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.Base64

final class EncodingEventGeneratingClient(out: PrintStream, standalone: Boolean)
  extends EventGeneratingClient(eventHandler(out, standalone), out.checkError) {

  private var _hasErrors = false

  def hasErrors: Boolean = _hasErrors

  override def message(msg: Client.ClientMsg): Unit = {
    if (msg.kind == MessageKind.Error)
      _hasErrors = true
    super.message(msg)
  }
}

object EncodingEventGeneratingClient {

  private def eventHandler(out: PrintStream, standalone: Boolean): Event => Unit =
    event => {
      val encoded = Base64.getEncoder.encodeToString(event.toBytes)
      val encodedNormalized = if (standalone && !encoded.endsWith("=")) encoded + "=" else encoded
      val bytes = encodedNormalized.getBytes(StandardCharsets.UTF_8)
      out.write(bytes)
    }
}