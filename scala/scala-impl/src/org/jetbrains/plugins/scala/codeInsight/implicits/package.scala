package org.jetbrains.plugins.scala.codeInsight

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.{&&, Parent, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

package object implicits {
  def nameOf(e: PsiNamedElement): String = e match {
    case member: ScMember => nameOf(member)
    case (_: ScReferencePattern) && Parent(Parent(member: ScMember with PsiNamedElement)) => nameOf(member)
    case it => it.name
  }

  private def nameOf(member: ScMember with PsiNamedElement) =
    Option(member.containingClass).map(_.name + ".").mkString + member.name
}
