package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace, ResolveState}
import lexer.ScalaTokenTypes
import toplevel.typedef.ScMember
import impl.ScalaPsiElementFactory
/**
 * @author ilyas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder with ScImportsHolder {

  def exprs : Seq[ScExpression] = findChildrenByClass(classOf[ScExpression])

  def lastExpr = findLastChild(classOf[ScExpression])
  /**
   * return last child except whitespaces, braces, and comments
   */
  def lastStatement = findLastChild(classOf[ScBlockStatement])

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
