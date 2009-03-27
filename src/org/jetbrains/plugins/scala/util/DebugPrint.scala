package org.jetbrains.plugins.scala.util

/**
 * @author ilyas
 */
object DebugPrint {

  var dLog : Boolean = true

  def displayLog = dLog
  def displayLog_= (b: Boolean) = {
    dLog = b
  }

  def println(st: String): Unit = {
    if (displayLog) {
      Console.println(st)
    }
  }    
}