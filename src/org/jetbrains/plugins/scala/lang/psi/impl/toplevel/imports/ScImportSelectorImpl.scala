package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import api.ScalaFile
import api.toplevel.imports.{ScImportSelectors, ScImportExpr, ScImportSelector}
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiDocumentManager}
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import parser.ScalaElementTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportSelectorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportSelector {
  override def toString: String = "ImportSelector"

  def importedName () = {
    val id = findChildByType(TokenSets.ID_SET)
    if (id == null) reference.refName else id.getText
  }

  def reference(): ScStableCodeReferenceElement = findChildByClass(classOf[ScStableCodeReferenceElement])

  def deleteSelector: Unit = {
    val expr: ScImportExpr = PsiTreeUtil.getParentOfType(this, classOf[ScImportExpr])
    if (expr.selectors.length + (if (expr.singleWildcard) 1 else 0) == 1) {
      expr.deleteExpr
    }
    val forward: Boolean = expr.selectors.apply(0) == this
    var node = this.getNode
    var prev = if (forward) node.getTreeNext else node.getTreePrev
    var t: IElementType = null
    do {
      node.getTreeParent.removeChild(node)
      node = prev
      if (node != null) {
        prev = if (forward) node.getTreeNext else node.getTreePrev
        t = node.getElementType
      }
    } while (node != null && !(t == ScalaElementTypes.IMPORT_SELECTOR || t == ScalaTokenTypes.tUNDER))

    //unnecessary braces removing
    if (expr.selectors.length + (if (expr.singleWildcard) 1 else 0) == 1) {
      expr.wildcardElement match {
        case Some(elem: PsiElement) => {
          expr.selectorSet match {
            case Some(sel: ScImportSelectors) => {
              sel.getParent.getNode.replaceChild(sel.getNode, ScalaPsiElementFactory.createWildcardNode(getManager))
            }
            case None => //can't be
          }
        }
        case _ => {
          val selector: ScImportSelector = expr.selectors.apply(0)
          if (selector.importedName == selector.reference.getText) {
            expr.getParent.getNode.replaceChild(expr.getNode, ScalaPsiElementFactory.createImportExprFromText(
                expr.qualifier.getText + "." + selector.reference.getText, getManager
              ).getNode)
          }
        }
      }
    }
  }
}