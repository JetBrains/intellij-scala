package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.statistics.ScalaDebuggerUsagesCollector.Group

import scala.annotation.nowarn

//noinspection UnstableApiUsage
class ScalaDebuggerUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object ScalaDebuggerUsagesCollector {
  private val Group = new EventLogGroup("scala.debugger", 1): @nowarn("cat=deprecation") // TODO: SCL-24479

  private val DebuggerEvent = Group.registerEvent("debugger"): @nowarn("cat=deprecation") // TODO: SCL-24479
  private val EvaluatorEvent = Group.registerEvent("evaluator"): @nowarn("cat=deprecation") // TODO: SCL-24479
  private val CompilingEvaluatorEvent = Group.registerEvent("compiling.evaluator"): @nowarn("cat=deprecation") // TODO: SCL-24479
  private val LambdaBreakpointEvent = Group.registerEvent("lambda.breakpoint"): @nowarn("cat=deprecation") // TODO: SCL-24479
  private val SmartStepIntoEvent = Group.registerEvent("smart.step.into"): @nowarn("cat=deprecation") // TODO: SCL-24479

  def logDebugger(project: Project): Unit = DebuggerEvent.log(project)
  def logEvaluator(project: Project): Unit = EvaluatorEvent.log(project)
  def logCompilingEvaluator(project: Project): Unit = CompilingEvaluatorEvent.log(project)
  def logLambdaBreakpoint(project: Project): Unit = LambdaBreakpointEvent.log(project)
  def logSmartStepInto(project: Project): Unit = SmartStepIntoEvent.log(project)
}
