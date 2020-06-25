package org.jetbrains.jps.incremental.scala.remote

import java.io.{File, PrintStream}
import java.util.Base64

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.EncodingEventGeneratingClient._

final class EncodingEventGeneratingClient(out: PrintStream, standalone: Boolean)
  extends EventGeneratingClient(eventHandler(out, standalone), out.checkError) {

  private var _hasErrors = false

  def hasErrors: Boolean = _hasErrors

  override def message(msg: Client.ClientMsg): Unit = {
    if (msg.kind == Kind.ERROR)
      _hasErrors = true
    super.message(msg)
  }
}

object EncodingEventGeneratingClient {

  private def eventHandler(out: PrintStream, standalone: Boolean): Event => Unit =
    event => {
      val encoded = Base64.getEncoder.encodeToString(event.toBytes)
      val encodedNormalized = if (standalone && !encoded.endsWith("=")) encoded + "=" else encoded
      val bytes = encodedNormalized.getBytes
      out.write(bytes)
    }
}