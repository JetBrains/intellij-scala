package org.jetbrains.plugins.scala.testingSupport.scalaTest


import collection.mutable.ArrayBuffer
import scalatest.tools.Runner

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.05.2009
 */

object ScalaTestRunner {
  def main(args: Array[String]) {
    val argsStart = new ArrayBuffer[String]
    val argsEnd = new ArrayBuffer[String]
    val classes = new ArrayBuffer[String]
    var i = 0
    while (i < args.length && args(i) != "-s") {
      argsStart += args(i)
      i = i + 1
    }
    argsStart += args(i)
    i = i + 1
    while (i < args.length && !args(i).startsWith("-")) {
      classes += args(i)
      i = i + 1
    }
    while (i < args.length) {
      argsEnd += args(i)
      i = i + 1
    }
    for (clazz <- classes) Runner.main(argsStart.toArray ++ Array(clazz) ++ argsEnd.toArray)
  }
}