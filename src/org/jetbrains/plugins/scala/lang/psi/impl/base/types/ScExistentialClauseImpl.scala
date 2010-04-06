package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.statements.ScDeclaredElementsHolder
import api.toplevel.ScNamedElement
import com.intellij.psi.scope.PsiScopeProcessor
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

import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/**
 * @author Alexander Podkhalyuzin
 * Date: 07.03.2008
 */

class ScExistentialClauseImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialClause {
  override def toString: String = "ExistentialClause"


  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent != null) {
      var run = lastParent
      while (run != null) {
        if (!processElement(run, processor, state)) return false
        run = run.getPrevSibling
      }
    }
    true
  }

  private def processElement(e: PsiElement, processor: PsiScopeProcessor, state: ResolveState): Boolean = e match {
    case named: ScNamedElement => processor.execute(named, state)
    case holder: ScDeclaredElementsHolder => {
      for (declared <- holder.declaredElements) {
        if (!processor.execute(declared, state)) return false
      }
      true
    }
    case _ => true
  }

}