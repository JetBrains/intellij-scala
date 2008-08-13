package org.jetbrains.plugins.scala.lang.psi.impl.statements

import types.{Nothing, ScFunctionType}
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import typedef._
import packaging.ScPackaging
import com.intellij.psi.scope._
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
      case _ => return false
    }
  }

  import com.intellij.openapi.util.Key

  def calcType = returnTypeElement match {
    case None => body match {
      case Some(b) => new ScFunctionType(b.getType, paramTypes)
      case _ => Nothing
    }
    case Some(rte) => new ScFunctionType(rte.getType, paramTypes)
  }
}