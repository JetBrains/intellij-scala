package org.jetbrains.plugins.scala
package lang
package completion
package filters.other

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class ExtendsFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val (leaf, isScriptFile) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    
    if (leaf != null) {
      var prev = getPrevSiblingNotWhitespace(leaf)
      val leafParent = leaf.getParent
      if (prev == null && leafParent != null && isScriptFile) prev = getPrevSiblingNotWhitespace(leafParent)
      prev match {
        case _: PsiErrorElement =>
        case _ => return false
      }
      val prev2 = prev.getPrevSibling
      prev2 match {
        case x: ScTypeDefinition =>
          if (x.extendsBlock.templateParents != None) return false
          else {
            if (leaf.getNextSibling != null &&
              leaf.getNextSibling.getNextSibling != null &&
              leaf.getNextSibling.getNextSibling.getNode.getElementType == ScalaTokenTypes.kEXTENDS) return false
            else return true
          }
        case _ => return false
      }
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "'extends' keyword filter"

  private def getPrevSiblingNotWhitespace(element: PsiElement): PsiElement = {
    var prev = element.getPrevSibling
    while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] ||
            prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) prev = prev.getPrevSibling
    prev
  }
}