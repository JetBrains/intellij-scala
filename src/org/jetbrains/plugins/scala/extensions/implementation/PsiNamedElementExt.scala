package org.jetbrains.plugins.scala.extensions.implementation

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.psi.{PsiClass, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * User: Alefas
 * Date: 16.02.12
 */
class PsiNamedElementExt(named: PsiNamedElement) {
  def name: String = {
    named match {
      case named: ScNamedElement => named.name
      case named => named.getName
    }
  }
}

class PsiMemberExt(member: PsiMember) {
  def containingClass: PsiClass = {
    member match {
      case member: ScMember => member.containingClass
      case _ => member.getContainingClass
    }
  }
}
