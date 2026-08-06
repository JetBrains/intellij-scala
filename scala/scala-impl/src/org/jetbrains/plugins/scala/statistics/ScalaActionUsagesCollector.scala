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
  private val Group = new EventLogGroup("scala.actions", 2) // TODO: SCL-24479

  private val TypeInfoEvent = Group.registerEvent("type.info") // TODO: SCL-24479
  private val XRayModeEvent = Group.registerEvent("x-ray.mode", new LongEventField("duration")) // TODO: SCL-24479
  private val ShowImplicitParametersEvent = Group.registerEvent("show.implicit.parameters") // TODO: SCL-24479
  private val GoToImplicitConversionEvent = Group.registerEvent("go.to.implicit.conversion") // TODO: SCL-24479
  private val StructureViewEvent = Group.registerEvent("structure.view") // TODO: SCL-24479
  private val OptimizeImportsEvent = Group.registerEvent("optimize.imports") // TODO: SCL-24479
  private val CreateFromUsageEvent = Group.registerEvent("createFromUsage") // TODO: SCL-24479
  private val OverrideImplementEvent = Group.registerEvent("overrideImplement") // TODO: SCL-24479
  private val DesugarCodeEvent = Group.registerEvent("desugar.code") // TODO: SCL-24479
  private val RearrangeEvent = Group.registerEvent("rearrange") // TODO: SCL-24479
  private val ConvertFromJavaEvent = Group.registerEvent("convert.javatext") // TODO: SCL-24479
  private val RunWorksheetEvent = Group.registerEvent("worksheet") // TODO: SCL-24479

  private val ScFileModeSetWorksheetEvent = Group.registerEvent("sc.file.set.worksheet") // TODO: SCL-24479
  private val ScFileModeSetAmmoniteEvent = Group.registerEvent("sc.file.set.ammonite") // TODO: SCL-24479
  private val ScFileModeSetAutoEvent = Group.registerEvent("sc.file.set.auto") // TODO: SCL-24479

  private val IncrementalityTypeSetSbtEvent = Group.registerEvent("compiler.inc.type.set.sbt") // TODO: SCL-24479
  private val IncrementalityTypeSetIdeaEvent = Group.registerEvent("compiler.inc.type.set.idea") // TODO: SCL-24479

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
