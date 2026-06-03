package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.statistics.ScalaAnnotatorUsagesCollector.Group

//noinspection UnstableApiUsage
class ScalaAnnotatorUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object ScalaAnnotatorUsagesCollector {
  private val Group = new EventLogGroup("scala.annotator", 1) // TODO: SCL-24479

  private val TypeAwareEvent = Group.registerEvent("file.with.type.aware.annotated") // TODO: SCL-24479
  private val NonTypeAwareEvent = Group.registerEvent("file.without.type.aware.annotated") // TODO: SCL-24479
  private val StructuralTypeEvent = Group.registerEvent("structural.type") // TODO: SCL-24479
  private val ExistentialTypeEvent = Group.registerEvent("existential.type") // TODO: SCL-24479
  private val MacroDefinitionEvent = Group.registerEvent("macro.definition") // TODO: SCL-24479
  private val CollectionTypeHighlightingEvent = Group.registerEvent("collection.pack.highlighting") // TODO: SCL-24479

  def logTypeAware(project: Project): Unit = TypeAwareEvent.log(project)
  def logNoneTypeAware(project: Project): Unit = NonTypeAwareEvent.log(project)
  def logStructuralType(project: Project): Unit = StructuralTypeEvent.log(project)
  def logExistentialType(project: Project): Unit = ExistentialTypeEvent.log(project)
  def logMacroDefinition(project: Project): Unit = MacroDefinitionEvent.log(project)
  def logCollectionTypeHighlighting(project: Project): Unit = CollectionTypeHighlightingEvent.log(project)
}
