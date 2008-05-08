package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import scala.lang.lexer._
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScTypedPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypedPattern{

  override def toString: String = "TypedPattern"

  def getIdentifierNodes: Array[PsiElement] = {
    if (getFirstChild.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
      val res = new Array[PsiElement](1)
      res(0) = getFirstChild.asInstanceOf[PsiElement]
      return res
    }
    else return new Array[PsiElement](0)
  }

}