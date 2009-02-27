package org.jetbrains.plugins.scala.compiler.rt

import _root_.scala.tools.nsc.GenericRunnerSettings
import _root_.scala.tools.nsc.Settings

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */

object ConsoleRunnerUtilClass {
  private def function(s: String): Unit = {
    System.out.println(s)
  }

  def getUnitFunction: String => Unit = {
    function(_)
  }

  def setParamParser(args: Array[String], settings: Settings) =
    settings.parseParams(args.mkString("|"), getNothingFunction);

  def getNothingFunction: String => Nothing = getUnitFunction.asInstanceOf[String => Nothing]

  def listOf(args: Array[String]) = args.toList

  def addQuotes(args: Array[String]) {
    for (i <- 0 to args.length - 1 if !args(i).startsWith("-")) args(i) = "\"" + args(i) + "\""
  }

  def getSettings(args: Array[String]): GenericRunnerSettings = {
    new GenericRunnerSettings(getUnitFunction) {
      override def parseParams(line: String, error: String => Nothing) {
        var args =
        if (line.trim() == "") Nil
        else List.fromArray(line.trim().split("[|]")).map(_.trim())
        while (!args.isEmpty) {
          val argsBuf = args
          if (args.head startsWith "-") {
            for (setting <- allSettings)
              args = setting.tryToSet(args);
          }
          else error("Parameter '" + args.head + "' does not start with '-'.")
          if (argsBuf eq args) {
            error("Parameter '" + args.head + "' is not recognised by Scalac.")
            args = args.tail
          }
        }
      }
    }
  }
}