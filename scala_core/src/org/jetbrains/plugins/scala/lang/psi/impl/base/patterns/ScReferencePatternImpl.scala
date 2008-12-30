package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import api.base.patterns._
import api.toplevel.imports.ScImportStmt
import com.intellij.psi.scope.PsiScopeProcessor
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import lang.lexer._
import psi.types.Nothing

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScReferencePatternImpl(node: ASTNode) extends ScBindingPatternImpl (node) with ScReferencePattern{
  override def toString: String = "ReferencePattern"
  override def calcType = expectedType match {
    case Some(t) => t
    case None => Nothing
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement) = lastParent match {
    case _: ScImportStmt if !isWildcard => {
      ScType.extractClassType(calcType) match {
        case Some((c, _)) => c.processDeclarations(processor, state, null, place)
        case _ => true
      }
    }
    case _ => true
  }
  
}