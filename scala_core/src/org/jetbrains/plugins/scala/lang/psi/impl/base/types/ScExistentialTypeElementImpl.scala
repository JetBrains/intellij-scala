package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import psi.ScalaPsiElementImpl
import api.base.types._
import api.statements.{ScTypeAliasDeclaration, ScValueDeclaration}
import lang.psi.types.ScExistentialType

import com.intellij.lang.ASTNode
import com.intellij.psi.{ResolveState, PsiElement}


/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScExistentialTypeElement{
  override def toString: String = "ExistentialType"

  override def getType() = ScExistentialType.create(quantified.getType, clause.declarations)

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent == quantified) {
      for (decl <- clause.declarations) {
        decl match {
          case alias : ScTypeAliasDeclaration => if (!processor.execute(alias, state)) return false
          case valDecl : ScValueDeclaration =>
            for (declared <- valDecl.declaredElements) if (!processor.execute(declared, state)) return false
        }
      }
    }
    true
  }
}