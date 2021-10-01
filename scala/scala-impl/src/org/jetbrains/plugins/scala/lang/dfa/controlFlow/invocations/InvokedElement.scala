package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.{PsiElement, PsiMember, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

case class InvokedElement(psiElement: PsiElement) {

  override def toString: String = psiElement match {
    case synthetic: ScSyntheticFunction => s"$synthetic: ${synthetic.name}"
    case function: PsiMethod => s"${function.containingClass.name}#${function.name}"
    case _ => s"Invoked element of unknown type: $psiElement"
  }

  def isSynthetic: Boolean = psiElement.is[ScSyntheticFunction]

  def name: Option[String] = psiElement match {
    case namedElement: PsiNamedElement => Some(namedElement.name)
    case _ => None
  }

  def containingClassName: Option[String] = psiElement match {
    case member: PsiMember => Some(member.containingClass.name)
    case _ => None
  }
}
