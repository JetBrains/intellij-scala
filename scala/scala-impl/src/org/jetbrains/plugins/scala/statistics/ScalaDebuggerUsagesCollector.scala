package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.statistics.ScalaDebuggerUsagesCollector.Group

//noinspection UnstableApiUsage
class ScalaDebuggerUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object ScalaDebuggerUsagesCollector {
  private val Group = new EventLogGroup("scala.debugger", 1)

  private val DebuggerEvent = Group.registerEvent("debugger")
  private val EvaluatorEvent = Group.registerEvent("evaluator")
  private val CompilingEvaluatorEvent = Group.registerEvent("compiling.evaluator")
  private val LambdaBreakpointEvent = Group.registerEvent("lambda.breakpoint")
  private val SmartStepIntoEvent = Group.registerEvent("smart.step.into")

  def logDebugger(project: Project): Unit = DebuggerEvent.log(project)
  def logEvaluator(project: Project): Unit = EvaluatorEvent.log(project)
  def logCompilingEvaluator(project: Project): Unit = CompilingEvaluatorEvent.log(project)
  def logLambdaBreakpoint(project: Project): Unit = LambdaBreakpointEvent.log(project)
  def logSmartStepInto(project: Project): Unit = SmartStepIntoEvent.log(project)
}
