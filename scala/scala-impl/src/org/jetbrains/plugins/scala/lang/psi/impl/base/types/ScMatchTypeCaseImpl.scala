package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScMatchTypeCase, ScTypeElement, ScTypeVariableTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScMatchTypeCaseImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMatchTypeCase {

  override def pattern: Option[ScTypePattern] = findChild[ScTypePattern]

  override def result: Option[ScTypeElement] = findLastChild[ScTypeElement]

  override def processDeclarations(processor: PsiScopeProcessor, state : ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    (pattern, result) match {
      case (Some(p), Some(r)) if lastParent != null && r.startOffsetInParent == lastParent.startOffsetInParent =>
        val typeVariables = p.typeElement.elements.filterByType[ScTypeVariableTypeElement]
        typeVariables.forall(processor.execute(_, state))
      case _ => true
    }
  }
}
