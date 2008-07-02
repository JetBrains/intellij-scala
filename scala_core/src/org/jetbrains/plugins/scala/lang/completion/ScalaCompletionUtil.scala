package org.jetbrains.plugins.scala.lang.completion

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._

/** 
* User: Alexander Podkhalyuzin
* Date: 21.05.2008.
*/

object ScalaCompletionUtil {
  def getLeafByOffset(offset: Int, element: PsiElement): PsiElement = {
    if (offset < 0) {
      return null
    }
    var candidate: PsiElement = element.getContainingFile()
    while (candidate.getNode().getChildren(null).length > 0) {
      candidate = candidate.findElementAt(offset)
    }
    return candidate
  }

  def getForAll(parent: PsiElement, leaf: PsiElement): (Boolean, Boolean) = {
    parent match {
      case _: ScalaFile => {
        if (leaf.getNextSibling!= null && leaf.getNextSibling().getNextSibling().isInstanceOf[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1)
          return (true,false)
      }
      case _ =>
    }
    parent match {
      case _: ScalaFile | _: ScPackaging => {
        var node = leaf.getPrevSibling
        if (node.isInstanceOf[PsiWhiteSpace]) node = node.getPrevSibling
        node match {
          case _: ScPackageStatement => return (true, false)
          case x: PsiErrorElement => {
            val s = ErrMsg("wrong.top.statment.declaration")
            x.getErrorDescription match {
              case `s` => return (true, true)
              case _ => return (true, false)
            }
          }
          case _ => return (true, true)
        }
      }
      case _ =>
    }
    parent.getParent match {
      case _: ScBlockExpr | _: ScTemplateBody => {
        if (awful(parent,leaf))
          return (true, true)
      }
      case _ =>
    }
    return (false, true)
  }

  def awful(parent: PsiElement, leaf: PsiElement): Boolean = {
    (leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
            leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF) && (parent.getPrevSibling == null ||
            parent.getPrevSibling.getPrevSibling == null ||
            (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementTypes.MATCH_STMT || !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement]))
  }

  val DUMMY_IDENTIFIER = "IntellijIdeaRulezzz"

  def checkEarlyDef(text: String, manager: PsiManager) : Boolean = {
    
  }
}