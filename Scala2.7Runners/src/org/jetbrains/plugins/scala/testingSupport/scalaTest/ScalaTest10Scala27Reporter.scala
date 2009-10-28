package org.jetbrains.plugins.scala.testingSupport.scalaTest

import org.scalatest.Reporter
import org.scalatest.events._
import java.io.{PrintWriter, StringWriter}

/**
 * User: Alexander Podkhalyuzin
 * Date: 27.10.2009
 */

class ScalaTest10Scala27Reporter extends Reporter {
  def apply(event: Event): Unit = {
    event match {
      case RunStarting(ordinal, testCount, configMap, formatter, payload, threadName, timeStamp) => {
        println("##teamcity[testCount count='" + testCount + "']")
      }
      case TestStarting(ordinal, suiteName, suiteClassName, testName, formatter, rerunnable, payload, threadName, timeStamp) => {
        println("\n##teamcity[testStarted name='" + escapeString(testName) +
            "' captureStandardOutput='true']")
      }
      case TestSucceeded(ordinal, suiteName, suiteClassName, testName, duration, formatter, rerunnable, payload, threadName, timeStamp) => {
        println("\n##teamcity[testFinished name='" + escapeString(testName) + "' duration='"+ duration.getOrElse(0) +"']")
      }
      case TestFailed(ordinal, message, suiteName, suiteClassName, testName, throwable,
                      duration, formatter, rerunnable, payload, threadName, timeStamp) => {
        var error = true
        val detail = throwable match {
          case Some(x) => {
            if (x.isInstanceOf[AssertionError]) error = false
            val writer = new StringWriter
            x.printStackTrace(new PrintWriter(writer))
            writer.getBuffer.toString
          }
          case None => ""
        }
        var res = "\n##teamcity[testFailed name='" + escapeString(testName) + "' message='" + escapeString(message) +
            "' details='" + escapeString(detail) + "'";
        if (error) res += "error = '" + error + "'";
        res += "timestamp='" + escapeString("" + timeStamp) + "']"
        println(res)
        println("\n##teamcity[testFinished name='" + escapeString(testName) + "' duration='"+ duration.getOrElse(0) +"']")
      }
      case TestIgnored(ordinal, suiteName, suiteClassName, testName, formatter, payload, threadName, timeStamp) => {
        println("\n##teamcity[testIgnored name='" + escapeString(testName) + "' message='" + escapeString("") + "']")
      }
      case TestPending(ordinal, suiteName, suiteClassName, testName, formatter, payload, threadName, timeStamp) =>
      case SuiteStarting(ordinal, suiteName, suiteClassName, formatter, rerunnable, payload, threadName, timeStamp) => {
        println("\n##teamcity[testSuiteStarted name='" + escapeString(suiteName) + "']")
      }
      case SuiteCompleted(ordinal, suiteName, suiteClassName, duration, formatter, rerunnable, payload, threadName, timeStamp) => {
        println("\n##teamcity[testSuiteFinished name='" + escapeString(suiteName) + "']")
      }
      case SuiteAborted(ordinal, message, suiteName, suiteClassName, throwable, duration, formatter, rerunnable, payload, threadName, timeStamp) =>
      case InfoProvided(ordinal, message, nameInfo, aboutAPendingTest, throwable, formatter, payload, threadName, timeStamp) =>
      case RunStopped(ordinal, duration, summary, formatter, payload, threadName, timeStamp) =>
      case RunAborted(ordinal, message, throwable, duration, summary, formatter, payload, threadName, timeStamp) =>
      case RunCompleted(ordinal, duration, summary, formatter, payload, threadName, timeStamp) =>
    }
  }

  private def escapeString(s: String): String = {
    s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]")
  }
}