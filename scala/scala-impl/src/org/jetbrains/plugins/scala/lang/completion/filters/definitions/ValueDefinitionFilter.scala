package org.jetbrains.plugins.scala
package lang
package completion
package filters.definitions

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ValueDefinitionFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      val parent = leaf.getParent
      
      parent.getParent match {
        case _: ScGenerator | _: ScForBinding | _: ScExistentialClause =>
          if ((leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
            leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF) &&
            (parent.getPrevSibling == null || parent.getPrevSibling.getPrevSibling == null ||
              (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementType.MATCH_STMT ||
                !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement])))
            return true
        case _ =>
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "val keyword filter"
  }
}