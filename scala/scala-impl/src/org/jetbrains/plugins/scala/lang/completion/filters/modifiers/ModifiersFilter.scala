package org.jetbrains.plugins.scala
package lang
package completion
package filters.modifiers

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiElement, _}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class ModifiersFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    if (element.isInstanceOf[PsiIdentifier]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))

    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScClassParameter =>
          return true
        case _ =>
      }
      val tuple = ScalaCompletionUtil.getForAll(parent,leaf)
      if (tuple._1) return tuple._2
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]) = true

  @NonNls
  override def toString = "modifiers keyword filter"
}