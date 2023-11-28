package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.ScFileMode
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector.Group

//noinspection UnstableApiUsage
class ScalaActionUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object ScalaActionUsagesCollector {
  private val Group = new EventLogGroup("scala.actions", 2)

  private val TypeInfoEvent = Group.registerEvent("type.info")
  private val XRayModeEvent = Group.registerEvent("x-ray.mode", new LongEventField("duration"))
  private val ShowImplicitParametersEvent = Group.registerEvent("show.implicit.parameters")
  private val GoToImplicitConversionEvent = Group.registerEvent("go.to.implicit.conversion")
  private val StructureViewEvent = Group.registerEvent("structure.view")
  private val OptimizeImportsEvent = Group.registerEvent("optimize.imports")
  private val CreateFromUsageEvent = Group.registerEvent("createFromUsage")
  private val OverrideImplementEvent = Group.registerEvent("overrideImplement")
  private val DesugarCodeEvent = Group.registerEvent("desugar.code")
  private val RearrangeEvent = Group.registerEvent("rearrange")
  private val ConvertFromJavaEvent = Group.registerEvent("convert.javatext")
  private val RunWorksheetEvent = Group.registerEvent("worksheet")

  private val ScFileModeSetWorksheetEvent = Group.registerEvent("sc.file.set.worksheet")
  private val ScFileModeSetAmmoniteEvent = Group.registerEvent("sc.file.set.ammonite")
  private val ScFileModeSetAutoEvent = Group.registerEvent("sc.file.set.auto")

  private val IncrementalityTypeSetSbtEvent = Group.registerEvent("compiler.inc.type.set.sbt")
  private val IncrementalityTypeSetIdeaEvent = Group.registerEvent("compiler.inc.type.set.idea")

  def logTypeInfo(project: Project): Unit = TypeInfoEvent.log(project)
  def logXRayMode(project: Project, duration: Long): Unit = XRayModeEvent.log(project, duration)
  def logShowImplicitParameters(project: Project): Unit = ShowImplicitParametersEvent.log(project)
  def logGoToImplicitConversion(project: Project): Unit = GoToImplicitConversionEvent.log(project)
  def logStructureView(project: Project): Unit = StructureViewEvent.log(project)
  def logOptimizeImports(project: Project): Unit = OptimizeImportsEvent.log(project)
  def logCreateFromUsage(project: Project): Unit = CreateFromUsageEvent.log(project)
  def logOverrideImplement(project: Project): Unit = OverrideImplementEvent.log(project)
  def logDesugarCode(project: Project): Unit = DesugarCodeEvent.log(project)
  def logRearrange(project: Project): Unit = RearrangeEvent.log(project)
  def logConvertFromJava(project: Project): Unit = ConvertFromJavaEvent.log(project)
  def logRunWorksheet(project: Project): Unit = RunWorksheetEvent.log(project)

  def logScFileModeSet(mode: ScFileMode, project: Project): Unit = mode match {
    case ScFileMode.Worksheet => ScFileModeSetWorksheetEvent.log(project)
    case ScFileMode.Ammonite => ScFileModeSetAmmoniteEvent.log(project)
    case ScFileMode.Auto => ScFileModeSetAutoEvent.log(project)
  }

  def logIncrementalityTypeSet(incrementalityType: IncrementalityType, project: Project): Unit = incrementalityType match {
    case IncrementalityType.SBT => IncrementalityTypeSetSbtEvent.log(project)
    case IncrementalityType.IDEA => IncrementalityTypeSetIdeaEvent.log(project)
  }
}
