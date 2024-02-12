package org.jetbrains.plugins.scala.lang.completion.filters.expression

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ElseFilter extends ElementFilter {

  import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._

  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)
    var parent = leaf.getParent
    if (parent.is[ScExpression] && parent.getPrevSibling != null &&
        parent.getPrevSibling.getPrevSibling != null) {
      val ifStmt = parent.getPrevSibling match {
        case x: ScIf => x
        case x if x.is[PsiWhiteSpace] || x.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE =>
          x.getPrevSibling match {
            case x: ScIf => x
            case _ => null
          }
        case _ => null
      }
      var text = ""
      if (ifStmt == null) {
        while (parent != null && !parent.is[ScIf]) parent = parent.getParent
        if (parent == null) return false
        text = parent.getText
        text = replaceLiteral(text, " else true")
      } else {
        text = ifStmt.getText + " else true"
      }
      checkElseWith(text, context = parent)
    } else {
      false
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString: String = "else keyword filter"
}