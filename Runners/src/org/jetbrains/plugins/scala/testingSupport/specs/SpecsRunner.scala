package org.jetbrains.plugins.scala.testingSupport.specs


import collection.mutable.{ArrayBuffer, HashMap}
import java.io.{PrintWriter, StringWriter}

import org.specs.runner.{NotifierRunner, OutputReporter, Notifier}
import org.specs.Specification
import org.specs.util.{Classes, SimpleTimer}

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */

object SpecsRunner {
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("The first argument should be the specification class name")
      return
    }
    val classes = new ArrayBuffer[String]
    var sysFilter = ".*"
    var exFilter = ".*"
    var i = 0
    while (i < args.length) {
      if (args(i).startsWith("-sus")) {
        sysFilter = args(i).substring(5)
        i = i + 1
      } else if (args(i).equals("-s")) {
        i = i + 1
        while (i < args.length && !args(i).startsWith("-")) {
          classes += args(i)
          i = i + 1
        }
      } else if (args(i).startsWith("-ex")) {
        exFilter = args(i).substring(4)
        i = i + 1
      } else {
        i = i + 1
      }
    }

    for (clazz <- classes) {
      var spec = Classes.createObject[Specification](clazz)
      spec match {
        case Some(s) => {
          (new NotifierRunner(s, new SpecsNotifier) {
            override def exampleFilterPattern: String = exFilter

            override def susFilterPattern: String = sysFilter
          }).reportSpecs
        }
        case None => {
          spec = Classes.createObject[Specification](clazz + "$")
          spec match {
            case Some(s) => (new NotifierRunner(s, new SpecsNotifier) {
              override def exampleFilterPattern: String = exFilter

              override def susFilterPattern: String = sysFilter
            }).reportSpecs
            case None => println("Scala Plugin internal error: no test class was found")
          }
        }
      }
    }
  }
}

class SpecsNotifier extends Notifier {
  private val map = new HashMap[String, Long]

  def exampleError(s: String, t: Throwable): Unit = {
    exampleFailed(s,t)//todo: replace code without if (error)
  }

  def systemCompleted(s: String): Unit = {
    println("\n##teamcity[testSuiteFinished name='" + escapeString(s) + "']")
  }

  def exampleSucceeded(s: String): Unit = {
    val duration = System.currentTimeMillis - map(s)
    println("\n##teamcity[testFinished name='" + escapeString(s) + "' duration='"+ duration +"']")
    map.excl(s)
  }

  def systemStarting(s: String): Unit = {
    println("##teamcity[testSuiteStarted name='" + escapeString(s) + "' location='scala://" + escapeString(s) + "']")
  }

  def exampleFailed(s: String, t: Throwable): Unit = {
    val duration = System.currentTimeMillis - map(s)
    var error = true
    val detail = {
        if (t.isInstanceOf[AssertionError]) error = false
        val writer = new StringWriter
        t.printStackTrace(new PrintWriter(writer))
        writer.getBuffer.toString
      }
    println("\n##teamcity[testFailed name='" + escapeString(s) + "' message='" + escapeString(s) +
            "' details='"+ escapeString(detail) +"'"
            + (if (error) "error = '" + error + "'" else "")+
            "timestamp='" + escapeString(s) + "']")
    exampleSucceeded(s)
  }

  def runStarting(i: Int): Unit = {
    println("##teamcity[testCount count='" + i + "']")
  }

  def exampleSkipped(s: String): Unit = {
    println("\n##teamcity[testIgnored name='" + escapeString(s) + "' message='" + escapeString(s) + "']")
  }

  def exampleStarting(s: String): Unit = {
    println("\n##teamcity[testStarted name='" + escapeString(s) +
            "' captureStandardOutput='true']")
    map.put(s, System.currentTimeMillis)
  }

  private def escapeString(s: String) = {
    s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]")
  }
}