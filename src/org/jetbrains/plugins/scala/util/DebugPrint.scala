package org.jetbrains.plugins.scala.util

/**
 * Author: Ilya Sergey
 */
object DebugPrint {

  var displayLog = true;

  def println(st: String): Unit = {
    Console.println(st)
  }
}