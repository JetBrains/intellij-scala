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

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportStmt{
  override def toString: String = "ScImportStatement"

  import com.intellij.psi._
  import scope._

  /*override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    for (e <- importExprs) {
      e.reference match {
        case Some(ref) =>
          ref.bind match {
            case Some (result) => {
              
            }
          }
      }
    }

    true
  }*/
}