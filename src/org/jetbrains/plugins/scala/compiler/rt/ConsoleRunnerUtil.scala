package org.jetbrains.plugins.scala.compiler.rt

import _root_.scala.tools.nsc.Settings

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

object ConsoleRunnerUtil {
  private def function(s: String): Unit = {
    System.out.println(s)
  }

  def getUnitFunction: String => Unit = {
    function(_)
  }

  def setParamParser(args: Array[String], settings: Settings) =
    settings.parseParams(args.mkString(" "), getNothingFunction);

  def getNothingFunction: String => Nothing = getUnitFunction.asInstanceOf[String => Nothing]

  def listOf(args: Array[String]) = args.toList
}