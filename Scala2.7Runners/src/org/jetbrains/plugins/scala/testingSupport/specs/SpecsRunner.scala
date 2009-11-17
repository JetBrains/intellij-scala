package org.jetbrains.plugins.scala.testingSupport.specs;

import org.specs.Specification
import org.specs.runner.NotifierRunner
import org.specs.util.Classes
import collection.mutable.ArrayBuffer

class SpecsRunner {
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("The first argument should be the specification class name")
      return
    }
    val classes: ArrayBuffer[String] = new ArrayBuffer[String]
    var sysFilter: String = ".*"
    var exFilter: String = ".*"
    var i: Int = 0
    while (i < args.length) {
      if (args(i).startsWith("-sus:")) {
        sysFilter = args(i).substring(5)
        i = i + 1
      } else if (args(i).equals("-s")) {
        i = i + 1
        while (i < args.length && !args(i).startsWith("-")) {
          classes += args(i)
          i = i + 1
        }
      } else if (args(i).startsWith("-ex:")) {
        exFilter = args(i).substring(4)
        i = i + 1
      } else {
        i = i + 1
      }
    }

    val finalSysFilter: String = sysFilter
    val finalExFilter: String = exFilter

    for (clazz <- classes) {
      val option: Option[Specification] = Classes.createObject(clazz)
      option match {
        case Some(s: Specification) => {
          try {
            new NotifierRunner(s, new SpecsNotifier)
          } catch {
            case e: Exception => e.printStackTrace
          }
        }
        case _ => {
          val option: Option[Specification] = Classes.createObject(clazz + "$")
          option match {
            case Some(s: Specification) => {
              try {
                new NotifierRunner(s, new SpecsNotifier)
              } catch {
                case e: Exception => e.printStackTrace
              }
            }
            case _ => System.out.println("Scala Plugin internal error: no test class was found")
          }
        }
      }
    }
  }
}
