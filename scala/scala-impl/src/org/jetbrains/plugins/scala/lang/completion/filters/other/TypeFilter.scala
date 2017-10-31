package org.jetbrains.plugins.scala
package lang
package completion
package filters.other

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.10.2008
 */

class TypeFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)

    val imp = leaf.parentOfType(classOf[ScImportStmt], strict = false)
    if (imp.isDefined) return false

    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScStableCodeReferenceElement => return true
        case _ => return false
      }
    }
    false
  }

  override def toString: String = "'type' keyword filter"

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true
}