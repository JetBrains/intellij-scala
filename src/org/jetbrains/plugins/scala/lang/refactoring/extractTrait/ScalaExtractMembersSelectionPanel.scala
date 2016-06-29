package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import java.util

import com.intellij.refactoring.classMembers.MemberInfoModel
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.refactoring.ui.{ScalaMemberSelectionPanelBase, ScalaMemberSelectionTableBase}

/**
 * Nikolay.Tropin
 * 2014-05-23
 */
class ScalaExtractMembersSelectionPanel(title: String,
                                        memberInfo: util.List[ScalaExtractMemberInfo],
                                        abstractColumnHeader: String)
        extends ScalaMemberSelectionPanelBase[ScMember, ScalaExtractMemberInfo](title, memberInfo, abstractColumnHeader) {

  override def createMemberSelectionTable(memberInfos: util.List[ScalaExtractMemberInfo], abstractColumnHeader: String): ScalaExtractMemberSelectionTable = {
    new ScalaExtractMemberSelectionTable(memberInfos, ExtractTraitInfoModel, abstractColumnHeader)
  }
}

class ScalaExtractMemberSelectionTable(memberInfos: util.Collection[ScalaExtractMemberInfo],
                                       memberInfoModel: MemberInfoModel[ScMember, ScalaExtractMemberInfo],
                                       abstractColumnHeader: String)
        extends ScalaMemberSelectionTableBase[ScMember, ScalaExtractMemberInfo](memberInfos, memberInfoModel, abstractColumnHeader) {

}
