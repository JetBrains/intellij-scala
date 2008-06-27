package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import psi.ScalaPsiElement
import expr.ScExpression
import toplevel.typedef.ScMember
import com.intellij.psi.PsiElement
import impl.ScalaPsiElementFactory  //todo remove

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScCodeBlock extends ScalaPsiElement {

  def exprs : Seq[ScExpression]

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.copy.getNode,before.getNode)
    getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager), before.getNode)
    return true
  }
}