package org.jetbrains.plugins.scala.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.statistics.ScalaRefactoringUsagesCollector.Group

//noinspection UnstableApiUsage
class ScalaRefactoringUsagesCollector extends CounterUsagesCollector {
  override def getGroup: EventLogGroup = Group
}

object ScalaRefactoringUsagesCollector {
  private val Group = new EventLogGroup("scala.refactoring", 1) // TODO: SCL-24479

  private val RenameLocalEvent = Group.registerEvent("rename.local") // TODO: SCL-24479
  private val RenameMemberEvent = Group.registerEvent("rename.member") // TODO: SCL-24479
  private val MoveFileEvent = Group.registerEvent("move.file") // TODO: SCL-24479
  private val MoveClassEvent = Group.registerEvent("move.class") // TODO: SCL-24479
  private val IntroduceVariableEvent = Group.registerEvent("introduce.variable") // TODO: SCL-24479
  private val IntroduceTypeAliasEvent = Group.registerEvent("introduce.type.alias") // TODO: SCL-24479
  private val IntroduceFieldEvent = Group.registerEvent("introduce.field") // TODO: SCL-24479
  private val IntroduceParameterEvent = Group.registerEvent("introduce.parameter") // TODO: SCL-24479
  private val ExtractMethodEvent = Group.registerEvent("extract.method") // TODO: SCL-24479
  private val ExtractTraitEvent = Group.registerEvent("extract.trait") // TODO: SCL-24479
  private val InlineEvent = Group.registerEvent("inline") // TODO: SCL-24479
  private val ChangeSignatureEvent = Group.registerEvent("change.signature") // TODO: SCL-24479

  def logRenameLocal(project: Project): Unit = RenameLocalEvent.log(project)
  def logRenameMember(project: Project): Unit = RenameMemberEvent.log(project)
  def logMoveFile(project: Project): Unit = MoveFileEvent.log(project)
  def logMoveClass(project: Project): Unit = MoveClassEvent.log(project)
  def logIntroduceVariable(project: Project): Unit = IntroduceVariableEvent.log(project)
  def logIntroduceTypeAlias(project: Project): Unit = IntroduceTypeAliasEvent.log(project)
  def logIntroduceFiled(project: Project): Unit = IntroduceFieldEvent.log(project)
  def logIntroduceParameter(project: Project): Unit = IntroduceParameterEvent.log(project)
  def logExtractMethod(project: Project): Unit = ExtractMethodEvent.log(project)
  def logExtractTrait(project: Project): Unit = ExtractTraitEvent.log(project)
  def logInline(project: Project): Unit = InlineEvent.log(project)
  def logChangeSignature(project: Project): Unit = ChangeSignatureEvent.log(project)
}
