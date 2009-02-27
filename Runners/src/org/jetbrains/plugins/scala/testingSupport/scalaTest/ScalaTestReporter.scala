package org.jetbrains.plugins.scala.testingSupport.scalaTest

import _root_.org.scalatest.{Report, Reporter}
import _root_.scala.collection.mutable.HashMap
import java.io.{PrintWriter, StringWriter}
import java.util.Date

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.02.2009
 */

class ScalaTestReporterClass extends Reporter {
  private val map = new HashMap[String, Long]

  override def testSucceeded(r: Report): Unit = {
    val duration = System.currentTimeMillis - map(r.name)
    println("\n##teamcity[testFinished name='" + escapeString(r.name) + "' duration='"+ duration +"']")
    map.excl(r.name)
  }

  override def testFailed(r: Report): Unit = {
    val duration = System.currentTimeMillis - map(r.name)
    var error = true
    val detail = r.throwable match {
      case Some(x: Throwable) => {
        if (x.isInstanceOf[AssertionError]) error = false
        val writer = new StringWriter
        x.printStackTrace(new PrintWriter(writer))
        writer.getBuffer.toString
      }
      case None => ""
    }
    println("\n##teamcity[testFailed name='" + escapeString(r.name) + "' message='" + escapeString(r.message) +
            "' details='"+ escapeString(detail) +"'"
            + (if (error) "error = '" + error + "'" else "")+
            "timestamp='" + escapeString(r.date.toString) + "']")
    testSucceeded(r)
  }

  override def suiteCompleted(r: Report): Unit = {
    println("\n##teamcity[testSuiteFinished name='" + escapeString(r.name) + "']")
  }

  override def testStarting(r: Report): Unit = {
    println("\n##teamcity[testStarted name='" + escapeString(r.name) +
            "' captureStandardOutput='true']")
    map.put(r.name, System.currentTimeMillis)
  }


  override def testIgnored(r: Report): Unit = {
    println("\n##teamcity[testIgnored name='" + escapeString(r.name) + "' message='" + escapeString(r.message) + "']")
  }

  override def suiteStarting(r: Report): Unit = {
    println("##teamcity[testSuiteStarted name='" + escapeString(r.name) + "' location='scala://" + escapeString(r.name) + "']")
  }

  override def runStarting(i: Int): Unit = {
    println("##teamcity[testCount count='" + i + "']")
  }

  private def escapeString(s: String) = {
    s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]")
  }
}