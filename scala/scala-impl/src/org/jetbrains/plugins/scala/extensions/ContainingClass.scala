package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiClass, PsiMember}

object ContainingClass {

  def unapply(member: PsiMember): Option[PsiClass] = member match {
    case null => None
    case _ => Option(member.containingClass)
  }
}