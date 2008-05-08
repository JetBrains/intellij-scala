package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScInfixPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScInfixPattern{

  override def toString: String = "InfixPattern"

  def getIdentifierNodes: Array[PsiElement] = {
    var res = new Array[PsiElement](0)
    for (child <- getChildren) {
      child match {
        case pat: ScPattern => {
          res = res ++ pat.getIdentifierNodes
        }
        case _ =>
      }
    }
    return res
  }
}