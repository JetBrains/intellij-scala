package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import parser.ScalaElementTypes
import stubs.ScImportSelectorStub
import api.toplevel.imports.{ScImportStmt, ScImportSelectors, ScImportExpr, ScImportSelector}

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportSelectorImpl extends ScalaStubBasedElementImpl[ScImportSelector] with ScImportSelector {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScImportSelectorStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ImportSelector"

  def importedName: String = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScImportSelectorStub].importedName
    }
    val id = findChildByType(TokenSets.ID_SET)
    if (id == null) reference.refName else id.getText
  }

  def reference: ScStableCodeReferenceElement = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScImportSelectorStub].reference
    }
    (getFirstChild match {case s: ScStableCodeReferenceElement => s case _ => null})
  }

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
  }

  def moveSelector(newQualifier: String, newName: String): Unit = {
    val stmt: ScImportStmt = PsiTreeUtil.getParentOfType(this, classOf[ScImportStmt])
    val importHolder = PsiTreeUtil.getParentOfType(stmt, classOf[ScImportsHolder])

    // TODO If an import expression already exists with `newQualifier`, append to it
    //      rather than adding a new import statement
    val selectorText = newName + (if (newName == importedName) "" else " => " + importedName)
    val stmtText: String = "import " + newQualifier + ".{" + selectorText + "}"
    val newStmt = ScalaPsiElementFactory.createImportFromText(stmtText, getManager)
    importHolder.addImportBefore(newStmt, stmt)

    deleteSelector

  }
}