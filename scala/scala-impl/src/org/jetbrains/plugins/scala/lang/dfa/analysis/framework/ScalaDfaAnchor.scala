package org.jetbrains.plugins.scala.lang.dfa.analysis.framework

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

sealed trait ScalaDfaAnchor extends DfaAnchor
sealed trait ScalaDfaAnchorWithPsiElement extends ScalaDfaAnchor {
  def psiElement: PsiElement
}
case class ScalaPsiElementDfaAnchor(override val psiElement: PsiElement) extends ScalaDfaAnchorWithPsiElement
case class ScalaStatementAnchor(statement: ScBlockStatement) extends ScalaDfaAnchorWithPsiElement {
  def psiElement: PsiElement = statement
}
