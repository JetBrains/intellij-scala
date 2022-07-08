package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import com.intellij.refactoring.classMembers.MemberInfoModel
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.refactoring.ui.{ScalaMemberSelectionPanelBase, ScalaMemberSelectionTableBase}

import java.util

class ScalaExtractMembersSelectionPanel(@Nls title: String,
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
