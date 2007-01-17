package org.jetbrains.plugins.scala.lang.completion.filters

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.TextFilter;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.NotFilter;
import com.intellij.psi.filters.position._;
import com.intellij.psi.filters.position.LeftNeighbour;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.parser._


class ElementTypeFilter(elem : IElementType, keyword: String) extends PositionElementFilter {

  override def isAcceptable (element: java.lang.Object, context: PsiElement): Boolean = {
    if (!(element.isInstanceOf[PsiElement])) return false
    val elementType = element.asInstanceOf[PsiElement].getNode().getElementType()
    val res = element.asInstanceOf[PsiElement].getText().contains(" "+keyword+" ")
    !res && (elementType equals elem)
  }

  override def toString: String  = {
    return "element type is ("+elem.toString+")"
  }

}