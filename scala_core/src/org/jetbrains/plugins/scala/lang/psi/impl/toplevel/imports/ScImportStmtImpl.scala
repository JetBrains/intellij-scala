package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import util.PsiTreeUtil

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportStmt {
  override def toString: String = "ScImportStatement"

  import scope._

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (e <- importExprs) {
      val elem = e.reference match {
        case Some(ref) if !PsiTreeUtil.isAncestor(ref, place, false) =>
            ref.bind match {
              case None => null
              case Some(result) => result.element
            }
        case _ => null
      }
      if (elem != null) {
        e.selectorSet match {
          case None =>
            if (e.singleWildcard) {
              if (!elem.processDeclarations(processor, state, null, place)) return false
            } else {
              if (!processor.execute(elem, state)) return false
            }
          case Some(set) => //todo
        }
      }
    }

    true
  }
}