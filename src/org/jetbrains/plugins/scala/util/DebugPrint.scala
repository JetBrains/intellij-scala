package org.jetbrains.plugins.scala
package util

/**
 * @author ilyas
 */
object DebugPrint {

  var dLog : Boolean = true

  def displayLog: Boolean = dLog
  def displayLog_= (b: Boolean): Unit = {
    dLog = b
  }

  def println(st: String): Unit = {
    if (displayLog) {
      Console.println(st)
    }
  }    
}