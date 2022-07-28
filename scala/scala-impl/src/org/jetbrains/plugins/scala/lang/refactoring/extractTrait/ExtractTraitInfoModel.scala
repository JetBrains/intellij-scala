package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import com.intellij.refactoring.classMembers.{AbstractMemberInfoModel, MemberInfoChange}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

object ExtractTraitInfoModel extends AbstractMemberInfoModel[ScMember, ScalaExtractMemberInfo] {
  override def isAbstractEnabled(member: ScalaExtractMemberInfo): Boolean = {
    member.getMember match {
      case _: ScDeclaration => false
      case _ => true
    }
  }

  override def memberInfoChanged(event: MemberInfoChange[ScMember, ScalaExtractMemberInfo]): Unit = super.memberInfoChanged(event)

  override def isFixedAbstract(member: ScalaExtractMemberInfo): java.lang.Boolean = member.getMember match {
    case _: ScDeclaration => true
    case _ => null
  }

  override def isAbstractWhenDisabled(member: ScalaExtractMemberInfo): Boolean = member.getMember match {
    case _: ScDeclaration => true
    case _ => false
  }
}
