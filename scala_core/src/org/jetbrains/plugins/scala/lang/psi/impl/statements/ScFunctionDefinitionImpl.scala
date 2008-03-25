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

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 22.02.2008
* Time: 9:54:29
* To change this template use File | Settings | File Templates.
*/

class ScFunctionDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionDefinition{

  //def getParameters = (paramClauses :\ (Nil: List[ScParam]))((y: ScParamClause, x: List[ScParam]) =>
  //  y.params.toList ::: x)

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

  /**
  *  Process declarations of parameters
  */
/*
  override def processDeclarations(processor: PsiScopeProcessor,
      substitutor: PsiSubstitutor,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve.processors._

    if (processor.isInstanceOf[ScalaLocalVariableResolveProcessor]){
        this.varOffset = processor.asInstanceOf[ScalaLocalVariableResolveProcessor].offset
      getVariable(processor, substitutor)
    } else true
  }
*/

  override def toString: String = "ScFunctionDefinition"

}