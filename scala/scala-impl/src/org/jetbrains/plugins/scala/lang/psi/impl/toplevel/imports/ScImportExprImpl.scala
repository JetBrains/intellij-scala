package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions.{ObjectExt, StubBasedExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportExprStub

/**
 * @author AlexanderPodkhalyuzin
* Date: 20.02.2008
 */

class ScImportExprImpl private (stub: ScImportExprStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, IMPORT_EXPR, node) with ScImportExpr {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScImportExprStub) = this(stub, null)

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ImportExpression"

  def isSingleWildcard: Boolean = byStubOrPsi(_.isSingleWildcard)(wildcardElement.nonEmpty)

  def wildcardElement: Option[PsiElement] =
    Option(findChildByType(ScalaTokenTypes.tUNDER))
      .orElse(selectorSet.flatMap(_.wildcardElement))

  def qualifier: ScStableCodeReferenceElement = {
    if (reference.isEmpty)
      throw new IncorrectOperationException()
    else if (!isSingleWildcard && selectorSet.isEmpty)
      reference.flatMap(_.qualifier).orNull
    else
      reference.get
  }

  def deleteExpr() {
    val parent = getParent.asInstanceOf[ScImportStmt]
    if (parent.importExprs.size == 1) {
      parent.getParent match {
        case x: ScImportsHolder => x.deleteImportStmt(parent)
        case _ =>
      }
    } else {
      val node = parent.getNode
      val remove = node.removeChild _
      val next = getNextSibling
      if (next != null) {
        def removeWhitespaceAfterComma(comma: ASTNode) {
          if (comma.getTreeNext != null && !comma.getTreeNext.getText.contains("\n") &&
            comma.getTreeNext.getText.trim.isEmpty) {
            remove(comma.getTreeNext)
          }
        }
        if (next.getText == ",") {
          val comma = next.getNode
          removeWhitespaceAfterComma(comma)
          remove(comma)
        } else {
          if (next.getNextSibling != null && next.getNextSibling.getText == ",") {
            val comma = next.getNextSibling
            removeWhitespaceAfterComma(comma.getNode)
            remove(next.getNode)
            remove(comma.getNode)
          } else {
            val prev = getPrevSibling
            if (prev != null) {
              if (prev.getText == ",") {
                remove(prev.getNode)
              } else {
                if (prev.getPrevSibling != null && prev.getPrevSibling.getText == ",") {
                  remove(prev.getPrevSibling.getNode)
                }
              }
            }
          }
        }
      } else {
        val prev = getPrevSibling
        if (prev != null) {
          if (prev.getText == ",") {
            remove(prev.getNode)
          } else {
            if (prev.getPrevSibling != null && prev.getPrevSibling.getText == ",") {
              val prevSibling = prev.getPrevSibling
              remove(prev.getNode)
              remove(prevSibling.getNode)
            }
          }
        }
      }
      remove(getNode)
    }
  }

  def selectorSet: Option[ScImportSelectors] =
    this.stubOrPsiChild(IMPORT_SELECTORS)

  def reference: Option[ScStableCodeReferenceElement] =
    byPsiOrStub(getFirstChild.asOptionOf[ScStableCodeReferenceElement])(_.reference)
}