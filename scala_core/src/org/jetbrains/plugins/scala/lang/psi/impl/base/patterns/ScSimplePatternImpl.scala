package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScConstructorPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScConstructorPattern{

  override def toString: String = "ConstructorPattern"

  def getIdentifierNodes: Array[PsiElement] = {
    if (findChildByClass(classOf[ScPatternArgumentList]) != null) {
      var res = new Array[PsiElement](0)
      for (pat <- findChildByClass(classOf[ScPatternArgumentList]).getPatterns) {
        res = res ++ pat.getIdentifierNodes
      }
      return res
    }
    else {
      return  new Array[PsiElement](0)
    }
  }

}