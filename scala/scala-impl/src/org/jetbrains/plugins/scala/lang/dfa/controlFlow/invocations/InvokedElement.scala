package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions.{PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

case class InvokedElement(psiElement: PsiElement) {

  override def toString: String = psiElement match {
    case synthetic: ScSyntheticFunction => s"$synthetic: ${synthetic.name}"
    case function: PsiMethod => s"${function.containingClass.name}#${function.name}"
    case _ => s"Invoked element of unknown type: $psiElement"
  }
}
