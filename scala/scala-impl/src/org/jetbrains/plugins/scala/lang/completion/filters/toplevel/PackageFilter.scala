package org.jetbrains.plugins.scala
package lang
package completion
package filters.toplevel

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

class PackageFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    
    if (leaf != null) {
      val parent = leaf.getParent.pipeIf(_.is[ScReferenceExpression])(_.getParent)
      if (parent.is[ScalaFile, ScPackaging]) {
        if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.is[ScPackaging] &&
                leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1) return false
        else {
          val node = leaf.getPrevSibling.pipeIf(_.is[PsiWhiteSpace])(_.getPrevSibling)
          node match {
            case x: PsiErrorElement =>
              val s = ErrMsg("wrong.top.statement.declaration")
              x.getErrorDescription match {
                case `s` => return true
                case _ => return false
              }
            case _ => return true
          }
        }
      }
    }

    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]) = true

  @NonNls
  override def toString = "'package' keyword filter"
}