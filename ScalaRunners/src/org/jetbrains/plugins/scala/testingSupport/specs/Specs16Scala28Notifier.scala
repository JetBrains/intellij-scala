package org.jetbrains.plugins.scala.testingSupport.specs

import java.io.{StringWriter, PrintWriter}
import collection.mutable.HashMap
import org.specs.runner.{Notifier, NotifierRunner}
import java.lang.{Throwable, String}

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.12.2009
 */

class Specs16Scala28Notifier extends Notifier {
  private var map: HashMap[String, Long] = new HashMap[String, Long]

  def runStarting(i: Int): Unit = {
    System.out.println("##teamcity[testCount count='" + i + "']")
  }

  def exampleStarting(s: String): Unit = {
    map.put(s, System.currentTimeMillis)
    System.out.println("\n##teamcity[testStarted name='" + escapeString(s) + "' captureStandardOutput='true']")
  }


  def exampleSucceeded(s: String): Unit = {
    val oldTime: Long = map.getOrElse(s, 0)
    var duration: Long = System.currentTimeMillis - oldTime
    System.out.println("\n##teamcity[testFinished name='" + escapeString(s) + "' duration='" + duration + "']")
    map.remove(s)
  }


  def exampleFailed(s: String, t: Throwable): Unit = {
    var error: Boolean = true
    var detail: String = null
    if (t.isInstanceOf[AssertionError]) error = false
    var writer: StringWriter = new StringWriter
    t.printStackTrace(new PrintWriter(writer))
    detail = writer.getBuffer.toString
    var res: String = "\n##teamcity[testFailed name='" + escapeString(s) + "' message='" + escapeString(s) + "' details='" + escapeString(detail) + "'"
    if (error) res += "error = '" + error + "'"
    res += "timestamp='" + escapeString(s) + "']"
    System.out.println(res)
    exampleSucceeded(s)
  }


  def exampleError(s: String, t: Throwable): Unit = {
    exampleFailed(s, t)
  }


  def exampleSkipped(s: String): Unit = {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(s) + "' message='" + escapeString(s) + "']")
  }


  def systemStarting(s: String): Unit = {
    System.out.println("##teamcity[testSuiteStarted name='" + escapeString(s) + "' locationHint='scala://" + escapeString(s) + "']")
  }


  def systemCompleted(s: String): Unit = {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(s) + "']")
  }


  private def escapeString(s: String): String = {
    return s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]", "|]")
  }

  def systemSkipped(name: String): Unit = {}

  def systemError(name: String, e: Throwable): Unit = {}

  def systemFailed(name: String, e: Throwable): Unit = {}

  def systemSucceeded(name: String): Unit = {}
}