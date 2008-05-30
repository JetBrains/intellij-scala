package org.jetbrains.plugins.scala.lang.completion.filters

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.filters.position.LeftNeighbour;

class LeftLeftNeighbour(filter: ElementFilter) extends LeftNeighbour (filter) {

  override def isAcceptable(element: java.lang.Object, context: PsiElement): Boolean = {
    if (!(element.isInstanceOf[PsiElement])) return false
    var previous = FilterUtil.getPreviousElement(element.asInstanceOf[PsiElement], false)
    if (previous != null && (previous.isInstanceOf[PsiElement])) {
      previous = FilterUtil.getPreviousElement(previous.asInstanceOf[PsiElement], false)
      if (previous != null) {
        return getFilter().isAcceptable(previous, context)
      }
      else {
        return false
      }
    }
    false
  }

}