package org.jetbrains.plugins.scala
package lang
package completion
package filters.modifiers

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class ImplicitFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    
    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScParameter =>
          val prev = parent.getPrevSibling
          if (prev != null && prev.getNode != null &&
            prev.getNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) return true
        case _ =>
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "'implicit' keyword filter"
}