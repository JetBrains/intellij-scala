package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.lexer._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScReferencePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScReferencePattern{
  override def toString: String = "ReferencePattern"

  def getIdentifierNodes: Array[PsiElement] = {
    if (getFirstChild.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER)
      return Array.apply(getFirstChild.asInstanceOf[PsiElement])
    else
      return new Array[PsiElement](0)
  }
}