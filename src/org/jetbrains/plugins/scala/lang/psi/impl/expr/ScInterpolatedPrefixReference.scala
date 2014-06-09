package org.jetbrains.plugins.scala
package lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl

/**
 * @author kfeodorov 
 * @since 09.03.14.
 */
class ScInterpolatedPrefixReference(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) {
  override def nameId: PsiElement = this

  override def handleElementRename(newElementName: String): PsiElement = {
   replace(ScalaPsiElementFactory.createInterpolatedStringPrefix(newElementName, getManager))
  }
}

