package org.jetbrains.plugins.scala.extensions.implementation

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.psi.{PsiClass, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * User: Alefas
 * Date: 16.02.12
 */


class PsiNamedElementExt(named: PsiNamedElement) {
  /**
   * Second match branch is for Java only.
   */
  def name: String = {
    named match {
      case named: ScNamedElement => named.name
      case named => named.getName
    }
  }
}

class PsiMemberExt(member: PsiMember) {
  /**
   * Second match branch is for Java only.
   */
  def containingClass: PsiClass = {
    member match {
      case member: ScMember => member.containingClass
      case _ => member.getContainingClass
    }
  }
}
