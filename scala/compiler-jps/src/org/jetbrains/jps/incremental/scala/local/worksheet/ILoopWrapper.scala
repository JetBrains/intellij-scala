package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.Flushable

// TODO: rename to something more abstract, ReplInstanceWrapper?
//  ILoop was in Scala 2, in Scala 3 it is ReplDriver
trait ILoopWrapper {
  def init(): Unit
  def shutdown(): Unit

  def reset(): Unit
  def processChunk(input: String): Boolean

  /** @return either PrintWriter (Scala 2) or PrintStream (Scala 3)
   *          do not use Either, use only java primitives */
  def getOutput: Flushable
}
