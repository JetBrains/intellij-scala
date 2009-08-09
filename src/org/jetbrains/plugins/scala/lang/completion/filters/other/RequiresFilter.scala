package org.jetbrains.plugins.scala
package lang
package completion
package filters.other

import com.intellij.lang.ASTNode
import psi.api.ScalaFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.05.2008
*/

class RequiresFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    var leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context)
    val file = leaf.getContainingFile.asInstanceOf[ScalaFile]
    if (leaf != null && file.isScriptFile) {
      leaf = leaf.getParent
    }
    if (leaf != null) {
      var prev = leaf.getPrevSibling
      if (prev == null && file.isScriptFile) prev = leaf.getParent.getPrevSibling
      prev match {
        case _: PsiErrorElement =>
        case _ => return false
      }
      val prev2 = prev.getPrevSibling
      prev2 match {
        case x: ScClass => {
          if (!x.extendsBlock.empty) return false
          else if (x.getText.indexOf(" requires ") != -1) return false
          else if (leaf.getNextSibling != null &&  leaf.getNextSibling.getNextSibling != null &&
            leaf.getNextSibling.getNextSibling.getNode.getElementType == ScalaTokenTypes.kREQUIRES) return false
          else return true
        }
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
    return "'requires' keyword filter"
  }
}