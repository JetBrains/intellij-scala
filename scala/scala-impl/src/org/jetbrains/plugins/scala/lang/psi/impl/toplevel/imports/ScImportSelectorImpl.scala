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
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets.{ID_SET, IMPORT_WILDCARDS}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.IMPORT_SELECTOR
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createImportExprWithContextFromText
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
      if (isWildcardSelector) None
      else Option(findChildByType[PsiElement](ID_SET)).map(_.getText)
        .orElse(reference.map(_.refName))
    }

  override def reference: Option[ScStableCodeReference] = byPsiOrStub {
    getFirstChild match {
      case element: ScStableCodeReference => Option(element)
      case _ => None
    }
  }(_.reference)

  override def isWildcardSelector: Boolean =
    byPsiOrStub(wildcardElement.isDefined)(_.isWildcardSelector)

  override def wildcardElement: Option[PsiElement] = {
    val firstChild = getFirstChild
    if (IMPORT_WILDCARDS.contains(firstChild.elementType)) Some(firstChild)
    else None
  }

  override def deleteSelector(removeRedundantBraces: Boolean): Unit = {
    val importExpr: ScImportExpr = PsiTreeUtil.getParentOfType(this, classOf[ScImportExpr])
    if (importExpr.selectors.length == 1) {
      importExpr.deleteExpr()
    }
    val forward: Boolean = importExpr.selectors.head == this
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

    if (removeRedundantBraces) {
      importExpr.deleteRedundantSingleSelectorBraces()
    }
  }

  override def isAliasedImport: Boolean = byStubOrPsi(_.isAliasedImport) {
    findChildByType(TokenSets.IMPORT_ALIAS_INDICATORS) != null
  }

  override def isGivenSelector: Boolean = byStubOrPsi(_.isGivenSelector) {
    findChildByType(ScalaTokenType.GivenKeyword) != null
  }

  override def givenTypeElement: Option[ScTypeElement] = byPsiOrStub(findChild[ScTypeElement])(_.typeElement)
}