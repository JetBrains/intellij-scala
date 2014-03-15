package org.jetbrains.plugins.scala
package lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * @author kfeodorov 
 * @since 15.03.14.
 */
class ScInterpolatedStringPartReference(node: ASTNode) extends ScReferenceExpressionImpl(node) {
  override def nameId: PsiElement = this.node.getPsi
  override def toString = s"InterpolatedStringPartReference: $getText"
}
