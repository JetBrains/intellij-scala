package org.jetbrains.jps.incremental.scala

import xsbti.{F0, Logger}

/**
 * @author Pavel Fatin
 */
private class ClientLogger(client: Client) extends Logger {
  def error(msg: F0[String]) {
    client.error(msg())
  }

  def warn(msg: F0[String]) {
    client.warning(msg())
  }

  def info(msg: F0[String]) {
//    client.info(msg())
    client.progress(msg())
  }

  def debug(msg: F0[String]) {
//    client.info(msg())
  }

  def trace(exception: F0[Throwable]) {
    client.trace(exception())
  }
}
