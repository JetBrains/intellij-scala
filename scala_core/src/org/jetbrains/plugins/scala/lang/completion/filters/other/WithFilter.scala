package org.jetbrains.plugins.scala.lang.completion.filters.other

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.05.2008
*/

class WithFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      val parent = leaf.getParent
      leaf.getPrevSibling match {
        case null => parent.getPrevSibling match {
          case _: ScType => return true
          case _ =>
        }
        case _: ScType => return true
        case _ =>
      }
      var prev = leaf.getPrevSibling
      prev match {
        case _: PsiWhiteSpace => prev = prev.getPrevSibling
        case _ => 
      }
      prev match {
        case _: PsiErrorElement =>
        case _ => return false
      }
      val prev2 = prev.getPrevSibling
      prev2 match {
        case x: ScTypeDefinition => return x.hasExtendsKeyword
        case _ => return false
      }
    }
    return false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true;
  }

  @NonNls
  override def toString(): String = {
    return "'with' keyword filter"
  }
}