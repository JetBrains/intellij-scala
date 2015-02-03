package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportExprStub

/**
 * @author AlexanderPodkhalyuzin
* Date: 20.02.2008
 */

class ScImportExprImpl extends ScalaStubBasedElementImpl[ScImportExpr] with ScImportExpr {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScImportExprStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ImportExpression"

  def singleWildcard: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScImportExprStub].isSingleWildcard
    }
    if (findChildByType[PsiElement](ScalaTokenTypes.tUNDER) != null) {
      true
    } else {
      selectorSet match {
        case Some(set) => set.hasWildcard
        case None => false
      }
    }
  }

  def wildcardElement: Option[PsiElement] = {
    if (findChildByType[PsiElement](ScalaTokenTypes.tUNDER) != null) {
      Some(findChildByType[PsiElement](ScalaTokenTypes.tUNDER))
    } else {
      selectorSet match {
        case Some(set) =>
          set.wildcardElement
        case None => None
      }
    }
  }

  def qualifier: ScStableCodeReferenceElement = if (!singleWildcard &&
      (selectorSet match {
        case None => true
        case _ => false
      })) reference match {
    case Some(x) => x.qualifier match {
      case None => null
      case Some(x) => x
    }
    case _ => throw new IncorrectOperationException
  } else reference match {
    case Some(x) => x
    case _ => throw new IncorrectOperationException
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


  def selectorSet: Option[ScImportSelectors] = {
    val psi: ScImportSelectors = getStubOrPsiChild(ScalaElementTypes.IMPORT_SELECTORS)
    if (psi == null) None
    else Some(psi)
  }

  def reference: Option[ScStableCodeReferenceElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScImportExprStub].reference
    } else (getFirstChild match {case s: ScStableCodeReferenceElement => Some(s) case _ => None})/*findChild(classOf[ScStableCodeReferenceElement])*/
  }
}