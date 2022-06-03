package org.jetbrains.plugins.scala.lang.dfa.invocationInfo

import com.intellij.psi.{PsiElement, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

case class InvokedElement(psiElement: PsiElement) {

  override def toString: String = psiElement match {
    case synthetic: ScSyntheticFunction => s"$synthetic: ${synthetic.name}"
    case namedMember: PsiNamedElement with PsiMember => Option(namedMember.containingClass) match {
      case Some(containingClass) => s"${containingClass.name}#${namedMember.name}"
      case _ => s"${namedMember.name}"
    }
    case _ => s"Invoked element of unknown type: $psiElement"
  }

  def simpleName: Option[String] = psiElement match {
    case namedElement: PsiNamedElement => Some(namedElement.name)
    case _ => None
  }

  def qualifiedName: Option[String] = psiElement match {
    case namedMember: PsiNamedElement with PsiMember => namedMember.qualifiedNameOpt
    case _ => None
  }

  def returnType: ScType = psiElement match {
    case synthetic: ScSyntheticFunction => synthetic.retType
    case function: ScFunction => function.returnType.getOrAny
    case _ => Any(psiElement.getProject)
  }
}

object InvokedElement {

  def fromTarget(target: Option[ScalaResolveResult], problems: Seq[ApplicabilityProblem]): Option[InvokedElement] = {
    if (problems.isEmpty) target.map(_.element).map(InvokedElement(_)) else None
  }
}
