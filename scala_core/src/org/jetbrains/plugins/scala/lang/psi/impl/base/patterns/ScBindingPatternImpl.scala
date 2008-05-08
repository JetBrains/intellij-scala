package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScBindingPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScBindingPattern{

  override def toString: String = "BindingPattern"

  //todo implement me!
  def isSeqBindingPattern: Boolean = false

  def getIdentifierNodes: Array[PsiElement] = {
    val res = new Array[PsiElement](1);
    res(0) = getFirstChild.asInstanceOf[PsiElement]
    if (findChildByClass(classOf[ScPattern]) != null) {
      return res ++ findChildByClass(classOf[ScPattern]).getIdentifierNodes
    }
    else
      return res
  }
}