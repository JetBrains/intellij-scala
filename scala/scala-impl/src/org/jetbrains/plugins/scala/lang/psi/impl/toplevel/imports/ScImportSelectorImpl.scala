package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{BooleanExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.TokenSets.ID_SET
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.IMPORT_SELECTOR
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createImportExprFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportSelectorStub

/**
  * @author Alexander Podkhalyuzin
  *         Date: 20.02.2008
  */
class ScImportSelectorImpl private(stub: ScImportSelectorStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, IMPORT_SELECTOR, node) with ScImportSelector {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScImportSelectorStub) = this(stub, null)

  override def toString: String = "ImportSelector"

  override def importedName: Option[String] =
    byStubOrPsi(_.importedName) {
      Option(findChildByType[PsiElement](ID_SET)).map(_.getText)
        .orElse(reference.map(_.refName))
    }

  override def reference: Option[ScStableCodeReference] = byPsiOrStub {
    getFirstChild match {
      case element: ScStableCodeReference => Option(element)
      case _ => None
    }
  }(_.reference)

  override def deleteSelector(): Unit = {
    val expr: ScImportExpr = PsiTreeUtil.getParentOfType(this, classOf[ScImportExpr])
    if (expr.selectors.length + expr.isSingleWildcard.toInt == 1) {
      expr.deleteExpr()
    }
    val forward: Boolean = expr.selectors.head == this
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
    } while (node != null && !(t == IMPORT_SELECTOR || t == ScalaTokenTypes.tUNDER || t == ScalaTokenType.WildcardStar))

    expr.selectors match {
      case Seq(sel: ScImportSelector) if !sel.isAliasedImport =>
        sel.reference.foreach { reference =>
          val withoutBracesText = expr.qualifier.fold("")(_.getText + ".") + reference.getText
          expr.replace(createImportExprFromText(withoutBracesText))
        }
      case _ =>
    }
  }

  override def isAliasedImport: Boolean = byStubOrPsi(_.isAliasedImport) {
    PsiTreeUtil.getParentOfType(this, classOf[ScImportExpr]).selectors.nonEmpty &&
        !getLastChild.is[ScStableCodeReference]
  }
}