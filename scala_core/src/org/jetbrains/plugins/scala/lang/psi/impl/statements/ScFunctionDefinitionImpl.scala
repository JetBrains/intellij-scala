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
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import typedef._
import packaging.ScPackaging
import com.intellij.psi.scope._
import types.Nothing

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionDefinitionImpl(node: ASTNode) extends ScFunctionImpl (node) with ScFunctionDefinition {

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    body match {
      case Some(x) if x == lastParent =>
        for (p <- parameters) {
          if (!processor.execute(p, state)) return false
        }
      case _ => 
    }
    
    true
  }

  override def toString: String = "ScFunctionDefinition"

  /**
  * Fake method to provide type-unsafe Scala Run Configuration
  */
  override def isMainMethod: Boolean = {
    val obj = getContainingClass
    if (!getName.equals("main") || !obj.isInstanceOf[ScObject]) return false
    obj.getParent match {
      case _: PsiFile | _: ScPackaging => {}
      case _ => return false
    }
    val pc = paramClauses
    if (pc == null) return false
    val params = pc.params
    if (params.length != 1) return false
    params(0).typeElement match {
      case Some(g: ScParameterizedTypeElement) => {
        if (!"Array".equals(g.simpleTypeElement.getText)) return false
        val args = g.typeArgList.typeArgs
        if (args.length != 1) return false
        args(0).getText == "String"
      }
      case None | Some(_) => return false
    }
  }

  def calcType = returnTypeElement match {
    case None => Nothing //todo inference
    case Some(rte) => rte.getType
  }
}