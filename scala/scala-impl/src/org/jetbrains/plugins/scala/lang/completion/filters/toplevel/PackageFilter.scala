package org.jetbrains.plugins.scala.lang.completion.filters.toplevel

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{NonNullObjectExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

class PackageFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    val parent = leaf.getParent.pipeIf(_.is[ScReferenceExpression])(_.getParent)
    if (!parent.is[ScalaFile, ScPackaging]) {
      return false
    }

    if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.is[ScPackaging] &&
            leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1) {
      return false
    }

    val node = leaf.getPrevSibling.pipeIf(_.is[PsiWhiteSpace])(_.getPrevSibling)
    node match {
      case x: PsiErrorElement =>
        val s = ErrMsg("wrong.top.statement.declaration")
        x.getErrorDescription match {
          case `s` => true
          case _ => false
        }
      case _ => true
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]) = true

  @NonNls
  override def toString = "'package' keyword filter"
}