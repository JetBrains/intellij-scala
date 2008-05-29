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

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportStmt {
  override def toString: String = "ScImportStatement"

  import com.intellij.psi._
  import scope._

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (e <- importExprs) {
      if (lastParent == e) return true
    }
    for (e <- importExprs) {
      val elem = e.reference match {
        case None => null
        case Some(ref) =>
          ref.qualifier match {
            case None => ref.refName match {
              case "_root_" => JavaPsiFacade.getInstance(getProject()).findPackage("")
              case name => ref.bind match {
                case None => JavaPsiFacade.getInstance(getProject()).findPackage(name)
                case Some(r) => r.element
              }
            }
            case Some(q) => ref.bind match {
              case None => null
              case Some(result) => result.element
            }
          }
      }

      if (elem != null) {
        //todo actually process import selectors
      }
    }

    true
  }
}