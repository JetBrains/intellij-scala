package org.jetbrains.plugins.scala.lang.psi.impl.statements

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl






import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:54:29
*/

class ScFunctionDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionDefinition{

  def getNameNode: ASTNode = node.findChildByType(ScalaTokenTypes.tIDENTIFIER)

  import com.intellij.psi.scope._
  def getVariable(processor: PsiScopeProcessor,
      substitutor: PsiSubstitutor): Boolean = {

    // Scan for parameters
/*
    for (val paramDef <- getParameters; paramDef.getTextOffset <= varOffset) {
      if (paramDef != null && ! processor.execute(paramDef, substitutor)) {
        return false
      }
    }
*/
    return true
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (lastParent == getBody) {
      val ps = getParameters()
      for (p <- ps) {
        if (!processor.execute(p.getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER).getPsi, state)) return false
      }
      true
    }
    else false
  }

  override def getIcon(flags: Int) = Icons.FUNCTION

  override def toString: String = "ScFunctionDefinition"

  def getBody: PsiElement = findChildByClass(classOf[ScExpression])

  def getParameters():Array[ScParam] = findChildByClass(classOf[ScParamClauses]).getParameters
}