package org.jetbrains.jps.incremental.scala

import java.io.File

import org.jetbrains.jps.incremental.messages.BuildMessage.Kind

/**
 * User: Dmitry.Naydanov
 * Date: 12.02.15.
 */
class DummyClient extends Client {

  override def message(msg: Client.ClientMsg): Unit = ()

  override def deleted(module: File): Unit = ()

  override def progress(text: String, done: Option[Float]): Unit = ()

  override def isCanceled: Boolean = false

  override def debug(text: String): Unit = ()

  override def trace(exception: Throwable): Unit = ()

  override def generated(source: File, module: File, name: String): Unit = ()
}
