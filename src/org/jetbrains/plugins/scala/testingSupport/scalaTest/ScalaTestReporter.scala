package org.jetbrains.plugins.scala.testingSupport.scalaTest

import _root_.org.scalatest.{Report, Reporter}

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.02.2009
 */

class ScalaTestReporter extends Reporter {
  override def testSucceeded(r: Report): Unit = {
    println("##teamcity[testFinished name='" + r.name + "']")
  }

  override def testFailed(r: Report): Unit = {
    println("##teamcity[testFailed name='" + r.name + "' message='" + r.message + "']")
  }

  override def suiteCompleted(r: Report): Unit = {
    println("##teamcity[testSuiteFinished name='" + r.name + "']")
  }

  override def testStarting(r: Report): Unit = {
    println("##teamcity[testStarted name='" + r.name +
            "' captureStandardOutput='true']")
  }


  override def testIgnored(r: Report): Unit = {
    println("##teamcity[testIgnored name='" + r.name + "; message='" + r.message + "']")
  }

  override def suiteStarting(r: Report): Unit = {
    println("##teamcity[testSuiteStarted name='" + r.name + "']")
  }

  override def runStarting(i: Int): Unit = {
    println("##teamcity[testCount count='" + i + "']")
  }
}