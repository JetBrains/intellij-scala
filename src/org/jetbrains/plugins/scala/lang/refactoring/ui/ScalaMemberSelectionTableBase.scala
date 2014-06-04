package org.jetbrains.plugins.scala
package lang.refactoring.ui

import com.intellij.psi.{PsiModifierList, PsiModifierListOwner, PsiElement}
import com.intellij.refactoring.classMembers.MemberInfoModel
import com.intellij.refactoring.ui.AbstractMemberSelectionTable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember}
import com.intellij.ui.RowIcon
import com.intellij.util.{IconUtil, VisibilityIcons}
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.icons.AllIcons

/**
 * Nikolay.Tropin
 * 8/20/13
 */
abstract class ScalaMemberSelectionTableBase[M <: PsiElement, I <: ScalaMemberInfoBase[M]](memberInfos: java.util.Collection[I],
                                memberInfoModel: MemberInfoModel[M, I],
                                abstractColumnHeader: String)
        extends AbstractMemberSelectionTable[M, I](memberInfos, memberInfoModel, abstractColumnHeader) {

  def getAbstractColumnValue(memberInfo: I): AnyRef = {
    memberInfo.getMember match {
      case member: ScMember if member.containingClass.isInstanceOf[ScObject] => null
      case member: ScMember if member.hasAbstractModifier && myMemberInfoModel.isFixedAbstract(memberInfo) != null =>
        myMemberInfoModel.isFixedAbstract(memberInfo)
      case _ if !myMemberInfoModel.isAbstractEnabled(memberInfo) =>
        val res: java.lang.Boolean = myMemberInfoModel.isAbstractWhenDisabled(memberInfo)
        res
      case _ if memberInfo.isToAbstract => java.lang.Boolean.TRUE
      case _ => java.lang.Boolean.FALSE
    }
  }

  def isAbstractColumnEditable(rowIndex: Int): Boolean = {
    val info: I = myMemberInfos.get(rowIndex)
    info.getMember match {
      case member: ScMember if member.hasAbstractModifier && myMemberInfoModel.isFixedAbstract(info) == java.lang.Boolean.TRUE => false
      case _ => info.isChecked && myMemberInfoModel.isAbstractEnabled(info)
    }
  }

  def setVisibilityIcon(memberInfo: I, icon: RowIcon) {
    memberInfo.getMember match {
      case owner: PsiModifierListOwner =>
        owner.getModifierList match {
          case mods: PsiModifierList => VisibilityIcons.setVisibilityIcon(mods, icon)
          case _ => icon.setIcon(IconUtil.getEmptyIcon(true), AbstractMemberSelectionTable.VISIBILITY_ICON_POSITION)
        }
      case _ =>
    }
  }

  def getOverrideIcon(memberInfo: I): Icon = memberInfo.getMember match {
    case fun: ScFunction =>
      if (java.lang.Boolean.TRUE == memberInfo.getOverrides) AllIcons.General.OverridingMethod
      else if (java.lang.Boolean.FALSE == memberInfo.getOverrides) AllIcons.General.ImplementingMethod
      else AbstractMemberSelectionTable.EMPTY_OVERRIDE_ICON
    case _ => AbstractMemberSelectionTable.EMPTY_OVERRIDE_ICON
  }
}
