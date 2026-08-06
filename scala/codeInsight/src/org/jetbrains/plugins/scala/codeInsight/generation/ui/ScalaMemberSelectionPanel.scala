package org.jetbrains.plugins.scala
package codeInsight.generation.ui

import java.{util => ju}

import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.ui._

import scala.jdk.CollectionConverters._

class ScalaMemberSelectionPanel(@Nls title: String, infos: ju.List[ScalaMemberInfo])
                               (model: AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo])
  extends ScalaMemberSelectionPanelBase[ScNamedElement, ScalaMemberInfo](title, infos, null) {

  protected def this(title: String,
                     members: Seq[ScNamedElement],
                     model: AbstractMemberInfoModel[ScNamedElement, ScalaMemberInfo]) =
    this(title, members.map(new ScalaMemberInfo(_)).asJava)(model)

  getTable.setMemberInfoModel(model)

  override def createMemberSelectionTable(memberInfos: ju.List[ScalaMemberInfo], abstractColumnHeader: String): ScalaMemberSelectionTableBase[ScNamedElement, ScalaMemberInfo] =
    new ScalaMemberSelectionPanel.Table(memberInfos, abstractColumnHeader)

  final def members: Iterable[ScNamedElement] =
    getTable.getSelectedMemberInfos.asScala.map(_.getMember)
}

object ScalaMemberSelectionPanel {

  private final class Table(memberInfos: ju.Collection[ScalaMemberInfo], abstractColumnHeader: String)
    extends ScalaMemberSelectionTableBase[ScNamedElement, ScalaMemberInfo](memberInfos, null, abstractColumnHeader)

}