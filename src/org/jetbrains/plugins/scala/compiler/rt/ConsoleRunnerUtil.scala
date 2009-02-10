package org.jetbrains.plugins.scala.compiler.rt

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

object ConsoleRunnerUtil {
  private def function(s: String): Unit = {
    System.out.println(s)
  }
  def getFunction: String => Unit = {
    function(_)
  }

  def listOf(args: Array[String]) = args.toList
}