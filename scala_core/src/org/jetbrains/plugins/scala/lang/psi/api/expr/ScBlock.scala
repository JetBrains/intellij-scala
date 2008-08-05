package org.jetbrains.plugins.scala.lang.psi.api.expr

import toplevel.typedef.ScMember
import com.intellij.psi.PsiElement
import impl.ScalaPsiElementFactory
/**
 * @author ilyas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder {

  def exprs : Seq[ScExpression] = findChildrenByClass(classOf[ScExpression])

  def lastExpr = {
    val exs = exprs
    exs.length match {
      case 0 => None
      case _ => Some(exs.last)
    }
  }

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.copy.getNode,before.getNode)
    getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager), before.getNode)
    return true
  }
}
