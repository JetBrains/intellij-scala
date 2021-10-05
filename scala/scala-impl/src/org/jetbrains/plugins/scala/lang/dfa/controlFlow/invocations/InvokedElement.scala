package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.{PsiElement, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.ApplicabilityProblem
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

case class InvokedElement(psiElement: PsiElement) {

  override def toString: String = psiElement match {
    case synthetic: ScSyntheticFunction => s"$synthetic: ${synthetic.name}"
    case namedMember: PsiNamedElement with PsiMember => s"${namedMember.containingClass.name}#${namedMember.name}"
    case _ => s"Invoked element of unknown type: $psiElement"
  }

  def isSynthetic: Boolean = psiElement.is[ScSyntheticFunction]

  def simpleName: Option[String] = psiElement match {
    case namedElement: PsiNamedElement => Some(namedElement.name)
    case _ => None
  }

  def qualifiedName: Option[String] = psiElement match {
    case namedMember: PsiNamedElement with PsiMember => namedMember.qualifiedNameOpt
    case _ => None
  }
}

object InvokedElement {

  def fromTarget(target: Option[ScalaResolveResult],
                 problems: Seq[ApplicabilityProblem]): Option[InvokedElement] = {
    if (problems.isEmpty) target.map(_.element).map(InvokedElement(_)) else None
  }
}
