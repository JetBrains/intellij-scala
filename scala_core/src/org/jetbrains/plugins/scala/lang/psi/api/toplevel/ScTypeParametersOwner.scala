package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor

trait ScTypeParametersOwner extends ScalaPsiElement {
  def typeParameters(): Seq[ScTypeParam] = typeParametersClause match {
    case Some(clause) => clause.typeParameters
    case _ => Seq.empty
  }

  def typeParametersClause = findChild(classOf[ScTypeParamClause])
}