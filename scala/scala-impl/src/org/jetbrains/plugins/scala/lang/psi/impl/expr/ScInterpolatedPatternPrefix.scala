package org.jetbrains.plugins.scala
package lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createInterpolatedStringPrefix
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl

/**
 * @author kfeodorov 
 * @since 09.03.14.
 */
class ScInterpolatedPatternPrefix(node: ASTNode) extends ScStableCodeReferenceImpl(node) {

  override protected def debugKind: Option[String] = Some("string interpolator")

  override def nameId: PsiElement = this

  override def handleElementRename(newElementName: String): PsiElement =
    replace(createInterpolatedStringPrefix(newElementName))
}

