package org.jetbrains.jps.incremental.scala.local.worksheet

import java.io.PrintWriter

/**
  * User: Dmitry.Naydanov
  * Date: 27.01.17.
  */
trait ILoopWrapper {
  def init()
  def shutdown()
  
  def reset()
  def processChunk(input: String): Boolean
  
  def getOutputWriter: PrintWriter 
}
