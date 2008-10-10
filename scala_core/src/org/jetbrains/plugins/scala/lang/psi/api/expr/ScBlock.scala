package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import toplevel.typedef.ScMember
import impl.ScalaPsiElementFactory
/**
 * @author ilyas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder with ScImportsHolder {

  def exprs : Seq[ScExpression] = findChildrenByClass(classOf[ScExpression])

  def lastExpr = {
    val exs = exprs
    exs.length match {
      case 0 => None
      case _ => Some(exs.last)
    }
  }

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.getNode,before.getNode)
    getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager), before.getNode)
    return true
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean =
    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
    super[ScImportsHolder].processDeclarations(processor, state, lastParent, place)
}
