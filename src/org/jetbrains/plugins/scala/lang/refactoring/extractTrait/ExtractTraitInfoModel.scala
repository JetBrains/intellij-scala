package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import com.intellij.refactoring.classMembers.{MemberInfoChange, AbstractMemberInfoModel}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration

/**
 * Nikolay.Tropin
 * 2014-05-23
 */
object ExtractTraitInfoModel extends AbstractMemberInfoModel[ScMember, ScalaExtractMemberInfo] {
  override def isAbstractEnabled(member: ScalaExtractMemberInfo) = {
    member.getMember match {
      case decl: ScDeclaration => false
      case _ => true
    }
  }

  override def memberInfoChanged(event: MemberInfoChange[ScMember, ScalaExtractMemberInfo]) = super.memberInfoChanged(event)

  override def isFixedAbstract(member: ScalaExtractMemberInfo) = member.getMember match {
    case decl: ScDeclaration => true
    case _ => null
  }

  override def isAbstractWhenDisabled(member: ScalaExtractMemberInfo) = member.getMember match {
    case decl: ScDeclaration => true
    case _ => false
  }
}
