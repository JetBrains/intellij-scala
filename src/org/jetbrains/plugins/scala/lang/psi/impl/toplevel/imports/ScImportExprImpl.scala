package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import com.intellij.util.IncorrectOperationException
import api.base.ScStableCodeReferenceElement
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._

/**
 * @author AlexanderPodkhalyuzin
* Date: 20.02.2008
 */

class ScImportExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportExpr {
  override def toString: String = "ImportExpression"

  def singleWildcard: Boolean = {
    if (findChildByType(ScalaTokenTypes.tUNDER) != null) {
      return true
    } else {
      selectorSet match {
        case Some(set) => set.hasWildcard
        case None => return false
      }
    }
  }

  def wildcardElement: Option[PsiElement] = {
    if (findChildByType(ScalaTokenTypes.tUNDER) != null) {
      Some(findChildByType(ScalaTokenTypes.tUNDER))
    } else {
      selectorSet match {
        case Some(set) => {
          set.wildcardElement
        }
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

  def deleteExpr {
    val parent = getParent.asInstanceOf[ScImportStmt]
    if (parent.importExprs.size == 1) {
      parent.getParent match {
        case x: ScImportsHolder => x.deleteImportStmt(parent)
        case _ =>
      }
    }
    else {
      val node = parent.getNode
      val remove = node.removeChild _
      val next = getNextSibling
      if (next != null) {
        if (next.getText == ",") {
          remove(next.getNode)
        } else {
          if (next.getNextSibling != null && next.getNextSibling.getText == ",") {
            remove(next.getNextSibling.getNode)
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
              remove(prev.getPrevSibling.getNode)
            }
          }
        }
      }
      remove(getNode)
    }
  }
}