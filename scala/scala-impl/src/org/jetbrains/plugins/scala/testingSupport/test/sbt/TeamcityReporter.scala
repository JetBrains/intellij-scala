package org.jetbrains.plugins.scala.testingSupport.test.sbt

import com.intellij.execution.process.{ProcessHandler, ProcessOutputTypes}

/**
 * Required in order IDEA can capture teamcity-notation message to draw a test tree in UI
 *
 * @see [[com.intellij.execution.testframework.sm.runner.OutputEventSplitter]]
 */
class TeamcityReporter(processHandler: ProcessHandler) {

  private def reportWithSpaces(message: String): Unit = {
    val messageWrapped = "\n" + message + "\n"
    processHandler.notifyTextAvailable(messageWrapped, TeamcityReporter.processOutputType)
  }

  def reportTestBase(stage: String)(name: String,  nodeId: Int, parentNodeId: Int)(otherAttributes: String): Unit =
    reportWithSpaces(s"##teamcity[$stage name='$name' nodeId='$nodeId' parentNodeId='$parentNodeId' $otherAttributes]")

  def reportTestStarted(name: String,  nodeId: Int, parentNodeId: Int): Unit =
    reportTestBase("testSuiteStarted")(name, nodeId, parentNodeId)("captureStandardOutput='true'")

  def reportTestFinished(name: String,  nodeId: Int, parentNodeId: Int, duration: Option[Long]): Unit =
    reportTestBase("testSuiteFinished")(name, nodeId, parentNodeId)(duration.fold("")(d => s"duration='$d'"))

  def reportTestFailure(name: String, nodeId: Int, parentNodeId: Int, duration: Option[Long], message: String, extraAttrs: String): Unit =
    reportTestBase("testFailed")(name, nodeId, parentNodeId)(s"message='$message' $extraAttrs ${duration.fold("")(d => s"duration='$d'")}")

  def reportTestIgnored(name: String, nodeId: Int, parentNodeId: Int, message: String): Unit =
    reportTestBase("testIgnored")(name, nodeId, parentNodeId)(s"message='$message'")
}

object TeamcityReporter {

  private val processOutputType = ProcessOutputTypes.STDOUT
}
